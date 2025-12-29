package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.zip.GZIPInputStream
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

/**
 * A DynamoDB client wrapper that intercepts query and scan responses to handle S3 pointers before they reach Tempest's
 * deserializer.
 *
 * This operates at the lowest level possible while still being generic. When we detect items with only pk, sk, and
 * _s3_pointer, we:
 * 1. Load the full data from S3
 * 2. Replace the minimal pointer item with the full item
 * 3. Pass the full item to Tempest for normal deserialization
 *
 * @param delegate The underlying DynamoDB client to wrap
 * @param s3Client The S3 client for fetching stored objects
 * @param objectMapper The JSON object mapper for deserializing S3 data
 * @param hybridConfig Configuration for S3 storage and performance
 * @param s3Executor Optional executor for parallel S3 reads. If null, reads will be sequential.
 *                   The caller is responsible for managing the lifecycle of this executor.
 */
class S3AwareDynamoDbClient(
  private val delegate: DynamoDbClient,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig,
  private val s3Executor: Executor? = null,
) : DynamoDbClient by delegate {

  companion object {
    private val logger = LoggerFactory.getLogger(S3AwareDynamoDbClient::class.java)

    // Track active executors to warn about lifecycle issues
    private val activeExecutors = mutableSetOf<WeakReference<Executor>>()
  }

  init {
    // Add a shutdown hook to warn about executor lifecycle issues
    s3Executor?.let { executor ->
      val executorRef = WeakReference(executor)
      activeExecutors.add(executorRef)

      // Only add one shutdown hook for all instances
      if (activeExecutors.size == 1) {
        Runtime.getRuntime().addShutdownHook(Thread {
          val stillActive = activeExecutors.mapNotNull { ref ->
            ref.get()?.let { exec ->
              when (exec) {
                is ExecutorService -> if (!exec.isShutdown) exec else null
                is ThreadPoolExecutor -> if (!exec.isShutdown) exec else null
                else -> null // Can't check other executor types
              }
            }
          }

          if (stillActive.isNotEmpty()) {
            logger.warn(
              "⚠️ WARNING: ${stillActive.size} executor(s) provided to S3AwareDynamoDbClient were not " +
              "properly shutdown. This may cause thread leaks and prevent JVM shutdown. " +
              "Please ensure you call shutdown() on your executor when done."
            )
          }

          // Clean up references
          activeExecutors.clear()
        })
      }
    }
  }

  /**
   * Records a metric event if metrics are configured.
   * No-op if metrics is null.
   */
  private fun recordMetric(event: MetricEvent) {
    hybridConfig.metrics?.record(event)
  }


  override fun query(request: QueryRequest): QueryResponse {
    logger.debug("Intercepting DynamoDB query for table: ${request.tableName()}")

    // Execute the original query
    val response = delegate.query(request)
    logger.debug("Original query returned ${response.items().size} items")

    // Hydrate items (either in parallel or sequentially based on config)
    val hydratedItems = hydrateItems(response.items(), "query")

    // Only rebuild response if we actually hydrated S3 items
    return if (hydratedItems !== response.items()) {
      val newResponse = response.toBuilder().items(hydratedItems).build()
      logger.debug("Query completed with S3 hydration")
      newResponse
    } else {
      logger.debug("Query completed without S3 hydration")
      response
    }
  }

  override fun scan(request: ScanRequest): ScanResponse {
    logger.debug("Intercepting DynamoDB scan")

    // Execute the original scan
    val response = delegate.scan(request)

    // Hydrate items (either in parallel or sequentially based on config)
    val hydratedItems = hydrateItems(response.items(), "scan")

    // Only rebuild response if we actually hydrated S3 items
    return if (hydratedItems !== response.items()) {
      response.toBuilder().items(hydratedItems).build()
    } else {
      response
    }
  }

  override fun getItem(request: GetItemRequest): GetItemResponse {
    logger.debug("Intercepting DynamoDB getItem")

    // Execute the original getItem
    val response = delegate.getItem(request)

    if (response.hasItem()) {
      val item = response.item()

      // Check if the item has an S3 pointer
      if (isS3Pointer(item)) {
        val hydratedItem = loadFromS3AndReplaceItem(item, "getItem")
        return if (hydratedItem != null) {
          response.toBuilder().item(hydratedItem).build()
        } else {
          // SKIP_FAILED strategy - return empty response
          response.toBuilder().item(emptyMap()).build()
        }
      }
    }

    return response
  }

  override fun batchGetItem(request: BatchGetItemRequest): BatchGetItemResponse {
    logger.debug("Intercepting DynamoDB batchGetItem")

    // Execute the original batchGetItem
    val response = delegate.batchGetItem(request)

    // Process each table's results
    val hydratedResponses = mutableMapOf<String, List<Map<String, AttributeValue>>>()
    var anyHydrated = false

    response.responses().forEach { (tableName, items) ->
      val hydratedItems = hydrateItems(items, "batchGetItem")
      hydratedResponses[tableName] = hydratedItems
      if (hydratedItems !== items) {
        anyHydrated = true
      }
    }

    // Only rebuild response if we actually hydrated S3 items
    return if (anyHydrated) {
      response.toBuilder().responses(hydratedResponses).build()
    } else {
      response
    }
  }

  override fun transactGetItems(request: TransactGetItemsRequest): TransactGetItemsResponse {
    logger.debug("Intercepting DynamoDB transactGetItems")

    // Execute the original transactGetItems
    val response = delegate.transactGetItems(request)

    // Process each response item
    val hydratedResponses = mutableListOf<ItemResponse>()
    var anyHydrated = false

    response.responses().forEach { itemResponse ->
      val item = itemResponse.item()
      if (item != null && item.isNotEmpty() && isS3Pointer(item)) {
        val hydratedItem = loadFromS3AndReplaceItem(item, "transactGetItems")
        if (hydratedItem != null) {
          hydratedResponses.add(itemResponse.toBuilder().item(hydratedItem).build())
        } else {
          // SKIP_FAILED strategy - add empty item response
          hydratedResponses.add(itemResponse.toBuilder().item(emptyMap()).build())
        }
        anyHydrated = true
      } else {
        hydratedResponses.add(itemResponse)
      }
    }

    // Only rebuild response if we actually hydrated S3 items
    return if (anyHydrated) {
      response.toBuilder().responses(hydratedResponses).build()
    } else {
      response
    }
  }

  override fun putItem(request: PutItemRequest): PutItemResponse {
    // Pass through write operations unchanged
    return delegate.putItem(request)
  }

  private fun isS3Pointer(item: Map<String, AttributeValue>): Boolean {
    // Simple check: if item has _s3_pointer field with s3:// prefix, it needs S3 loading
    val s3PointerValue = item["_s3_pointer"]?.s()
    return s3PointerValue?.startsWith("s3://") == true
  }

  private fun loadFromS3AndReplaceItem(
    pointerItem: Map<String, AttributeValue>,
    operation: String = "unknown"
  ): Map<String, AttributeValue>? {
    val s3Pointer = pointerItem["_s3_pointer"]?.s() ?: return pointerItem

    val metrics = hybridConfig.metrics
    val start = if (metrics != null) System.currentTimeMillis() else 0L

    return try {
      // Extract bucket and key from S3 pointer
      // S3 pointers are in format: s3://path/to/key where path is relative to the bucket
      val s3Uri = s3Pointer.removePrefix("s3://")

      // Always use the configured bucket name
      val bucket = hybridConfig.s3Config.bucketName
      val key = s3Uri

      logger.debug("Loading from S3: bucket=$bucket, key=$key")

      // Load from S3
      val s3Object = s3Client.getObject(bucket, key)
      val bytes = s3Object.objectContent.readAllBytes()

      // Decompress if GZIP
      val decompressed =
        if (isGzipped(bytes)) {
          GZIPInputStream(bytes.inputStream()).readAllBytes()
        } else {
          bytes
        }

      // Parse JSON to get the full object
      val jsonNode = objectMapper.readTree(decompressed)

      // Convert JSON to DynamoDB AttributeValue map
      val fullItem = mutableMapOf<String, AttributeValue>()

      // Add all fields from the S3 object, INCLUDING pk and sk
      // The pk/sk in S3 must match exactly what was used during encryption
      jsonNode.fields().forEach { (fieldName, fieldValue) ->
        fullItem[fieldName] = jsonToAttributeValue(fieldName, fieldValue)
      }

      // Keep the S3 pointer for reference
      fullItem["_s3_pointer"] = pointerItem["_s3_pointer"]!!

      logger.debug("Successfully hydrated item from S3 (${fullItem.size} total fields)")

      // Record success metric
      if (metrics != null) {
        recordMetric(MetricEvent.Hydration(operation, true, System.currentTimeMillis() - start))
      }

      fullItem
    } catch (e: Exception) {
      // Record failure metric
      if (metrics != null) {
        recordMetric(MetricEvent.Hydration(operation, false, System.currentTimeMillis() - start))
      }

      // Handle based on error strategy
      when (hybridConfig.errorStrategy) {
        HybridConfig.ErrorStrategy.FAIL_FAST -> {
          logger.error("S3 hydration failed for $s3Pointer - failing fast", e)
          throw S3HydrationException(s3Pointer, e)
        }
        HybridConfig.ErrorStrategy.RETURN_POINTER -> {
          logger.warn("S3 hydration failed for $s3Pointer - returning pointer item", e)
          pointerItem
        }
        HybridConfig.ErrorStrategy.SKIP_FAILED -> {
          logger.warn("S3 hydration failed for $s3Pointer - skipping item", e)
          null
        }
      }
    }
  }


  /**
   * Converts JSON data from S3 into DynamoDB AttributeValues.
   *
   * This handles two JSON formats:
   * 1. DynamoDB JSON format: Objects with type indicators like {"S": "value"} or {"N": "123"}
   * 2. Regular JSON format: Plain JSON that needs conversion to DynamoDB types
   *
   * @param fieldName The field name (unused but kept for backwards compatibility)
   * @param node The JSON node to convert
   * @return The corresponding DynamoDB AttributeValue
   */
  private fun jsonToAttributeValue(fieldName: String, node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    // Check if this is DynamoDB-formatted JSON (single-field object with type indicator)
    if (isDynamoDbFormat(node)) {
      return convertDynamoDbFormat(node)
    } else {
      // Regular JSON format
      return convertRegularJson(node)
    }
  }

  /**
   * Checks if a JSON node is in DynamoDB format (e.g., {"S": "value"})
   */
  private fun isDynamoDbFormat(node: com.fasterxml.jackson.databind.JsonNode): Boolean {
    if (!node.isObject || node.size() != 1) return false

    val fieldName = node.fieldNames().next()
    return fieldName in setOf("S", "N", "B", "BOOL", "NULL", "SS", "NS", "BS", "L", "M")
  }

  /**
   * Converts DynamoDB-formatted JSON to AttributeValue.
   * Format: {"TypeIndicator": value} where TypeIndicator is S, N, B, BOOL, NULL, SS, NS, BS, L, or M
   */
  private fun convertDynamoDbFormat(node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    val field = node.fields().next()
    val type = field.key
    val value = field.value

    return when (type) {
      "S" -> AttributeValue.builder().s(value.asText()).build()
      "N" -> AttributeValue.builder().n(value.asText()).build()
      "B" -> convertBinary(value.asText())
      "BOOL" -> AttributeValue.builder().bool(value.asBoolean()).build()
      "NULL" -> AttributeValue.builder().nul(true).build()
      "SS" -> AttributeValue.builder().ss(value.map { it.asText() }.toSet()).build()
      "NS" -> AttributeValue.builder().ns(value.map { it.asText() }.toSet()).build()
      "BS" -> convertBinarySet(value)
      "L" -> convertList(value)
      "M" -> convertMap(value)
      else -> {
        // Unknown type - treat as regular JSON
        logger.debug("Unknown DynamoDB type indicator: $type")
        convertRegularJson(node)
      }
    }
  }

  /**
   * Converts regular JSON to DynamoDB AttributeValue.
   * Rules:
   * - Primitives (string, number, boolean, null) map directly
   * - Arrays of strings become String Sets (SS)
   * - Mixed arrays become Lists (L)
   * - Objects ALWAYS become JSON strings (for Tempest compatibility)
   */
  private fun convertRegularJson(node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    return when {
      node.isNull -> AttributeValue.builder().nul(true).build()
      node.isBoolean -> AttributeValue.builder().bool(node.asBoolean()).build()
      node.isNumber -> convertNumber(node)
      node.isTextual -> AttributeValue.builder().s(node.asText()).build()
      node.isArray -> convertArray(node)
      node.isObject -> {
        // IMPORTANT: Tempest stores all complex objects as JSON strings.
        // This includes custom types like Money, GlobalAddress, Metadata, etc.
        // Example: Money stored as {"amountCents":100,"currency":"USD"}
        AttributeValue.builder().s(node.toString()).build()
      }
      else -> AttributeValue.builder().s(node.toString()).build()
    }
  }

  /**
   * Converts a JSON number to DynamoDB number format
   */
  private fun convertNumber(node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    val numberString = if (node.isIntegralNumber) {
      node.asLong().toString()
    } else {
      node.asDouble().toString()
    }
    return AttributeValue.builder().n(numberString).build()
  }

  /**
   * Converts a JSON array to either a String Set or List
   */
  private fun convertArray(node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    // If all elements are strings, convert to String Set
    if (node.all { it.isTextual }) {
      val stringSet = node.map { it.asText() }.toSet()
      return if (stringSet.isNotEmpty()) {
        AttributeValue.builder().ss(stringSet).build()
      } else {
        AttributeValue.builder().nul(true).build()
      }
    }

    // Mixed types - convert to List
    val list = node.map { jsonToAttributeValue("", it) }
    return AttributeValue.builder().l(list).build()
  }

  /**
   * Converts a base64-encoded binary string to DynamoDB binary
   */
  private fun convertBinary(base64String: String): AttributeValue {
    val bytes = java.util.Base64.getDecoder().decode(base64String)
    return AttributeValue.builder()
      .b(software.amazon.awssdk.core.SdkBytes.fromByteArray(bytes))
      .build()
  }

  /**
   * Converts a JSON array of base64 strings to DynamoDB binary set
   */
  private fun convertBinarySet(value: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    val binarySet = value.map { element ->
      val bytes = java.util.Base64.getDecoder().decode(element.asText())
      software.amazon.awssdk.core.SdkBytes.fromByteArray(bytes)
    }
    return AttributeValue.builder().bs(binarySet).build()
  }

  /**
   * Converts a DynamoDB List type from JSON
   */
  private fun convertList(value: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    val list = value.map { jsonToAttributeValue("", it) }
    return AttributeValue.builder().l(list).build()
  }

  /**
   * Converts a DynamoDB Map type from JSON.
   *
   * When the source is DynamoDB-formatted JSON (with "M" type indicator),
   * we preserve it as a proper DynamoDB Map with nested AttributeValues.
   */
  private fun convertMap(value: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    // DynamoDB-formatted Maps stay as Maps with proper AttributeValues
    val map = mutableMapOf<String, AttributeValue>()
    value.fields().forEach { (key, nestedValue) ->
      map[key] = jsonToAttributeValue(key, nestedValue)
    }
    return AttributeValue.builder().m(map).build()
  }

  /**
   * Hydrates a list of items, fetching from S3 either in parallel or sequentially based on the provided executor
   */
  private fun hydrateItems(
    items: List<Map<String, AttributeValue>>,
    operation: String
  ): List<Map<String, AttributeValue>> {
    // First, identify which items need hydration
    val itemsWithIndices = items.mapIndexedNotNull { index, item ->
      if (isS3Pointer(item)) {
        index to item
      } else {
        null
      }
    }

    // If no items need hydration, return original list
    if (itemsWithIndices.isEmpty()) {
      return items
    }

    logger.debug("Found ${itemsWithIndices.size} items needing S3 hydration")

    // Hydrate items either in parallel or sequentially
    val hydratedMap = if (s3Executor != null && itemsWithIndices.size > 1) {
      // Parallel mode using provided executor
      logger.debug("Hydrating ${itemsWithIndices.size} items in parallel")

      val futures = itemsWithIndices.map { (index, item) ->
        CompletableFuture.supplyAsync({
          index to loadFromS3AndReplaceItem(item, operation)
        }, s3Executor)
      }

      // Wait for all futures to complete and collect results
      CompletableFuture.allOf(*futures.toTypedArray()).join()
      futures.associate { it.get() }
    } else {
      // Sequential mode (either no executor provided or single item)
      val mode = if (s3Executor == null) "sequentially (no executor provided)" else "sequentially (single item)"
      logger.debug("Hydrating ${itemsWithIndices.size} items $mode")
      itemsWithIndices.associate { (index, item) ->
        index to loadFromS3AndReplaceItem(item, operation)
      }
    }

    // Count successful hydrations for batch metrics
    val successCount = hydratedMap.values.count { it != null }

    // Record batch completion metric if we have metrics enabled
    if (hybridConfig.metrics != null && itemsWithIndices.isNotEmpty()) {
      recordMetric(MetricEvent.BatchComplete(operation, itemsWithIndices.size, successCount))
    }

    // Build the result list with hydrated items in their original positions
    // Filter out null entries (SKIP_FAILED strategy)
    return items.mapIndexedNotNull { index, item ->
      val hydratedItem = hydratedMap[index]
      when {
        hydratedItem != null -> hydratedItem // Item was hydrated
        hydratedMap.containsKey(index) -> null // Key exists but value is null (SKIP_FAILED)
        else -> item // Item didn't need hydration
      }
    }
  }

  private fun isGzipped(bytes: ByteArray): Boolean {
    return bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
  }
}