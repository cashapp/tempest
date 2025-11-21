package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
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
 */
class S3AwareDynamoDbClient(
  private val delegate: DynamoDbClient,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig,
) : DynamoDbClient by delegate {

  companion object {
    private val logger = LoggerFactory.getLogger(S3AwareDynamoDbClient::class.java)
  }

  override fun query(request: QueryRequest): QueryResponse {
    logger.warn("🔍 Intercepting DynamoDB query for table: ${request.tableName()}")

    // Execute the original query
    val response = delegate.query(request)
    logger.warn("  Original query returned ${response.items().size} items")

    // Check if any items need S3 hydration
    var hasS3Pointers = false
    val hydratedItems =
      response.items().map { item ->
        if (isS3Pointer(item)) {
          logger.warn("  🔗 Found S3 pointer item with ${item.size} fields, loading from S3...")
          hasS3Pointers = true
          val hydrated = loadFromS3AndReplaceItem(item)
          logger.warn("  ✅ After S3 hydration: ${hydrated.size} fields")
          hydrated
        } else {
          // Pass through regular items unmodified
          item
        }
      }

    // Only rebuild response if we actually hydrated S3 items
    return if (hasS3Pointers) {
      val newResponse = response.toBuilder().items(hydratedItems).build()
      logger.warn("  ✅ Query completed WITH S3 hydration, returning ${newResponse.items().size} items")
      newResponse
    } else {
      logger.warn("  ✅ Query completed WITHOUT S3 hydration, returning ${response.items().size} items")
      response
    }
  }

  override fun scan(request: ScanRequest): ScanResponse {
    logger.debug("Intercepting DynamoDB scan")

    // Execute the original scan
    val response = delegate.scan(request)

    // Check if any items have S3 pointers
    var hasS3Pointers = false
    val hydratedItems =
      response.items().map { item ->
        if (isS3Pointer(item)) {
          hasS3Pointers = true
          loadFromS3AndReplaceItem(item)
        } else {
          item
        }
      }

    // Only rebuild response if we actually hydrated S3 items
    return if (hasS3Pointers) {
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
        val hydratedItem = loadFromS3AndReplaceItem(item)
        return response.toBuilder().item(hydratedItem).build()
      }
    }

    return response
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

  private fun loadFromS3AndReplaceItem(pointerItem: Map<String, AttributeValue>): Map<String, AttributeValue> {
    val s3Pointer = pointerItem["_s3_pointer"]?.s() ?: return pointerItem

    try {
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

      logger.warn("Successfully hydrated item from S3 (${fullItem.size} total fields)")
      logger.warn("  Hydrated fields: ${fullItem.keys.sorted().joinToString()}")

      // Log some key values for debugging
      fullItem["customer_token"]?.s()?.let { logger.warn("  customer_token: $it") }
      fullItem["account_token"]?.s()?.let { logger.warn("  account_token: $it") }
      fullItem["transaction_token"]?.s()?.let { logger.warn("  transaction_token: $it") }
      fullItem["year"]?.n()?.let { logger.warn("  year: $it") }
      fullItem["month"]?.n()?.let { logger.warn("  month: $it") }

      return fullItem
    } catch (e: com.fasterxml.jackson.core.JsonParseException) {
      logger.error("JSON Parse Error in S3 file at ${e.location}: ${e.message}")
      logger.error("S3 URI: ${pointerItem["_s3_pointer"]?.s()}")
      logger.error("This usually means the JSON has unescaped special characters like newlines")
      logger.error("Check line ${e.location?.lineNr} column ${e.location?.columnNr} in your S3 JSON file")
      // Return the original pointer item - Tempest will likely fail to deserialize it
      return pointerItem
    } catch (e: Exception) {
      logger.error("Failed to load from S3: ${e.message}", e)
      logger.error("S3 URI: ${pointerItem["_s3_pointer"]?.s()}")
      // Return the original pointer item - Tempest will likely fail to deserialize it
      return pointerItem
    }
  }

  private fun jsonToAttributeValue(fieldName: String, node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    // Check if this is a DynamoDB-formatted JSON with type indicators
    if (node.isObject && node.size() == 1) {
      val field = node.fields().next()
      val type = field.key
      val value = field.value

      return when (type) {
        "S" -> AttributeValue.builder().s(value.asText()).build()
        "N" -> AttributeValue.builder().n(value.asText()).build()
        "B" ->
          AttributeValue.builder()
            .b(software.amazon.awssdk.core.SdkBytes.fromByteArray(java.util.Base64.getDecoder().decode(value.asText())))
            .build()
        "BOOL" -> AttributeValue.builder().bool(value.asBoolean()).build()
        "NULL" -> AttributeValue.builder().nul(true).build()
        "SS" -> AttributeValue.builder().ss(value.map { it.asText() }.toSet()).build()
        "NS" -> AttributeValue.builder().ns(value.map { it.asText() }.toSet()).build()
        "BS" ->
          AttributeValue.builder()
            .bs(
              value.map {
                software.amazon.awssdk.core.SdkBytes.fromByteArray(java.util.Base64.getDecoder().decode(it.asText()))
              }
            )
            .build()
        "L" -> AttributeValue.builder().l(value.map { jsonToAttributeValue("", it) }).build()
        "M" -> {
          // For empty maps, some converters expect JSON strings like "{}" instead of Map types
          // This handles cases where fields were stored as JSON strings but exported as empty Maps
          if (value.size() == 0) {
            // Return empty JSON string for empty maps - converters that expect strings will work
            // and converters that expect maps will fail, but empty maps are edge cases
            AttributeValue.builder().s("{}").build()
          } else {
            // Check if this looks like a simple JSON object that should be a string
            // If all values are primitives, it's likely meant to be a JSON string
            val shouldBeJsonString =
              value.fields().asSequence().all { (_, v) -> v.isTextual || v.isNumber || v.isBoolean || v.isNull }

            if (shouldBeJsonString) {
              // Convert to JSON string for simple objects
              val jsonMap = mutableMapOf<String, Any?>()
              value.fields().forEach { (k, v) ->
                jsonMap[k] =
                  when {
                    v.isNull -> null
                    v.isBoolean -> v.asBoolean()
                    v.isNumber -> if (v.isIntegralNumber) v.asLong() else v.asDouble()
                    v.isTextual -> v.asText()
                    else -> v.toString()
                  }
              }
              AttributeValue.builder().s(objectMapper.writeValueAsString(jsonMap)).build()
            } else {
              // Complex nested structure - keep as Map
              val map = mutableMapOf<String, AttributeValue>()
              value.fields().forEach { (k, v) -> map[k] = jsonToAttributeValue(k, v) }
              AttributeValue.builder().m(map).build()
            }
          }
        }
        else -> {
          // Unknown type, try to handle as regular JSON
          handleRegularJson(node)
        }
      }
    } else {
      // Not DynamoDB-formatted, handle as regular JSON
      return handleRegularJson(node)
    }
  }

  private fun handleRegularJson(node: com.fasterxml.jackson.databind.JsonNode): AttributeValue {
    return when {
      node.isNull -> AttributeValue.builder().nul(true).build()
      node.isBoolean -> AttributeValue.builder().bool(node.asBoolean()).build()
      node.isNumber -> {
        // All numbers stored as DynamoDB numbers
        if (node.isIntegralNumber) {
          AttributeValue.builder().n(node.asLong().toString()).build()
        } else {
          AttributeValue.builder().n(node.asDouble().toString()).build()
        }
      }
      node.isTextual -> AttributeValue.builder().s(node.asText()).build()
      node.isArray -> {
        // Special handling for arrays: if it's a Set<String>, convert to DynamoDB String Set
        if (node.all { it.isTextual }) {
          val stringSet = node.map { it.asText() }.toSet()
          if (stringSet.isNotEmpty()) {
            AttributeValue.builder().ss(stringSet).build()
          } else {
            AttributeValue.builder().nul(true).build()
          }
        } else {
          // For mixed arrays, use LIST
          val list = node.map { jsonToAttributeValue("", it) }
          AttributeValue.builder().l(list).build()
        }
      }
      node.isObject -> {
        // CRITICAL: Tempest stores all complex objects as JSON strings, not as DynamoDB MAPs
        // This includes Money, GlobalAddress, Metadata, and all other custom types

        // Money objects need to be stored as JSON strings with amountCents field
        // DynamoDB format: {"amountCents":-450,"currency":"USD"}
        AttributeValue.builder().s(node.toString()).build()
      }
      else -> AttributeValue.builder().s(node.toString()).build()
    }
  }

  private fun isGzipped(bytes: ByteArray): Boolean {
    return bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
  }
}