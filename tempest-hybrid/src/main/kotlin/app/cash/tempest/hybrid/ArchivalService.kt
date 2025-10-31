package app.cash.tempest.hybrid

import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import app.cash.tempest.InlineView
import app.cash.tempest.Scannable
import app.cash.tempest.Offset
import app.cash.tempest.Page
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Service responsible for archiving old data from DynamoDB to S3
 */
internal class ArchivalService(
  private val logicalDb: LogicalDb,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig
) {

  companion object {
    private val logger = LoggerFactory.getLogger(ArchivalService::class.java)
  }

  data class HybridTableInfo(
    val tableName: String,
    val tableClass: KClass<*>,
    val itemClass: KClass<*>,
    val hybridAnnotation: HybridTable,
    val archiveAfterDuration: Duration,
    val timestampProperty: KProperty1<Any, *>?
  )

  suspend fun archiveOldData(dryRun: Boolean = false): ArchivalResult = withContext(Dispatchers.IO) {
    if (!hybridConfig.archivalConfig.enabled) {
      logger.info("Archival is disabled in configuration")
      return@withContext ArchivalResult(0, 0, emptyList())
    }

    val processed = mutableListOf<ArchiveItem>()
    val errors = mutableListOf<String>()

    try {
      // Discover tables with @HybridTable annotation
      val hybridTables = discoverHybridTables()

      logger.info("Found ${hybridTables.size} hybrid tables to process")

      for (tableInfo in hybridTables) {
        try {
          logger.info("Processing table: ${tableInfo.tableName}")
          val result = archiveTable(tableInfo, dryRun)
          processed.addAll(result)
        } catch (e: Exception) {
          val error = "Failed to archive ${tableInfo.tableName}: ${e.message}"
          logger.error(error, e)
          errors.add(error)
        }
      }

      val archivedCount = processed.count { it.archived }
      logger.info("Archival complete. Processed: ${processed.size}, Archived: $archivedCount, Errors: ${errors.size}")

      ArchivalResult(
        itemsProcessed = processed.size,
        itemsArchived = archivedCount,
        errors = errors
      )
    } catch (e: Exception) {
      logger.error("Archival failed with unexpected error", e)
      ArchivalResult(0, 0, listOf("Archival failed: ${e.message}"))
    }
  }

  private fun discoverHybridTables(): List<HybridTableInfo> {
    val tables = mutableListOf<HybridTableInfo>()

    // Use reflection to find all tables in the LogicalDb
    logicalDb::class.memberProperties.forEach { property ->
      try {
        property.isAccessible = true
        val value = property.get(logicalDb)

        if (value is LogicalTable<*>) {
          // Check if the table's item class has @HybridTable annotation
          val itemClass = findItemClass(value) ?: return@forEach
          val hybridAnnotation = itemClass.findAnnotation<HybridTable>() ?: return@forEach

          // Find timestamp property with @ArchivalTimestamp
          val timestampProperty = itemClass.memberProperties.find { prop ->
            prop.findAnnotation<ArchivalTimestamp>() != null
          }

          if (timestampProperty == null) {
            logger.warn("Table ${property.name} has @HybridTable but no @ArchivalTimestamp property")
            return@forEach
          }

          val archiveDuration = Duration.ofDays(hybridAnnotation.archiveAfterDays.toLong())

          tables.add(
            HybridTableInfo(
              tableName = property.name,
              tableClass = value::class,
              itemClass = itemClass,
              hybridAnnotation = hybridAnnotation,
              archiveAfterDuration = archiveDuration,
              timestampProperty = timestampProperty as KProperty1<Any, *>
            )
          )
        }
      } catch (e: Exception) {
        logger.debug("Could not process property ${property.name}: ${e.message}")
      }
    }

    return tables
  }

  private fun findItemClass(table: LogicalTable<*>): KClass<*>? {
    // This is a simplified approach - in production, you'd want more robust type discovery
    try {
      // Try to find an InlineView and get its item type
      table::class.memberProperties.forEach { prop ->
        prop.isAccessible = true
        val value = prop.get(table)
        if (value is InlineView<*, *>) {
          // Use reflection to get the item type - this is a simplification
          return value::class.supertypes.firstOrNull()?.arguments?.getOrNull(1)?.type?.classifier as? KClass<*>
        }
      }
    } catch (e: Exception) {
      logger.debug("Could not find item class for table: ${e.message}")
    }
    return null
  }

  private suspend fun archiveTable(
    tableInfo: HybridTableInfo,
    dryRun: Boolean
  ): List<ArchiveItem> = withContext(Dispatchers.IO) {
    val threshold = Instant.now() - tableInfo.archiveAfterDuration
    val results = mutableListOf<ArchiveItem>()

    logger.info("Archiving items older than $threshold for table ${tableInfo.tableName}")

    // Get the table's scan method
    val table = getTableFromDb(tableInfo.tableName) ?: run {
      logger.error("Could not access table ${tableInfo.tableName}")
      return@withContext emptyList()
    }

    if (table !is Scannable<*, *>) {
      logger.error("Table ${tableInfo.tableName} is not scannable")
      return@withContext emptyList()
    }

    // Scan table for old items
    var offset: Offset<*>? = null
    var totalScanned = 0

    do {
      val page = table.scan(initialOffset = offset) as Page<*, *>
      totalScanned += page.contents.size

      for (item in page.contents) {
        if (item == null) continue

        try {
          // Check if item is already archived (has s3Key)
          if (isPointer(item)) {
            logger.debug("Item already archived, skipping")
            continue
          }

          // Check if item is old enough to archive
          val timestamp = extractTimestamp(item, tableInfo.timestampProperty)
          if (timestamp == null || timestamp.isAfter(threshold)) {
            logger.debug("Item not old enough to archive: $timestamp")
            continue
          }

          if (dryRun) {
            logger.info("[DRY RUN] Would archive item with timestamp $timestamp")
            results.add(ArchiveItem(item, archived = true, dryRun = true))
          } else {
            archiveItem(tableInfo, item, table)
            results.add(ArchiveItem(item, archived = true))
          }

        } catch (e: Exception) {
          logger.error("Failed to archive item: ${e.message}", e)
          results.add(ArchiveItem(item, archived = false, error = e.message))
        }
      }

      offset = page.offset

      // Log progress
      if (totalScanned % 100 == 0) {
        logger.info("Scanned $totalScanned items, archived ${results.count { it.archived }}")
      }

    } while (offset != null)

    results
  }

  private fun getTableFromDb(tableName: String): Any? {
    return try {
      val property = logicalDb::class.memberProperties.find { it.name == tableName }
      property?.isAccessible = true
      property?.get(logicalDb)
    } catch (e: Exception) {
      logger.error("Could not get table $tableName from LogicalDb", e)
      null
    }
  }

  private fun isPointer(item: Any): Boolean {
    return try {
      val s3KeyField = item::class.memberProperties.find { it.name == "s3Key" }
      s3KeyField?.isAccessible = true
      val s3Key = (s3KeyField as? KProperty1<Any, *>)?.get(item)
      s3Key != null
    } catch (e: Exception) {
      false
    }
  }

  private fun extractTimestamp(item: Any, timestampProperty: KProperty1<Any, *>?): Instant? {
    return try {
      timestampProperty?.isAccessible = true
      timestampProperty?.get(item) as? Instant
    } catch (e: Exception) {
      logger.debug("Could not extract timestamp: ${e.message}")
      null
    }
  }

  private suspend fun archiveItem(
    tableInfo: HybridTableInfo,
    item: Any,
    table: Any
  ) = withContext(Dispatchers.IO) {
    logger.debug("Archiving item from table ${tableInfo.tableName}")

    // 1. Generate S3 key
    val s3Key = S3KeyGenerator.generateS3Key(
      item,
      tableInfo.hybridAnnotation.s3KeyTemplate,
      tableInfo.tableName
    )

    val fullS3Key = if (hybridConfig.s3Config.keyPrefix.isNotEmpty()) {
      "${hybridConfig.s3Config.keyPrefix}/$s3Key"
    } else {
      s3Key
    }

    logger.debug("Generated S3 key: $fullS3Key")

    // 2. Compress and save to S3
    val json = objectMapper.writeValueAsString(item)
    val compressed = compressGzip(json.toByteArray())

    val metadata = ObjectMetadata().apply {
      contentType = "application/json"
      contentEncoding = "gzip"
      contentLength = compressed.size.toLong()
      addUserMetadata("archived_at", Instant.now().toString())
      addUserMetadata("table_name", tableInfo.tableName)
    }

    val putRequest = PutObjectRequest(
      hybridConfig.s3Config.bucketName,
      fullS3Key,
      ByteArrayInputStream(compressed),
      metadata
    )

    s3Client.putObject(putRequest)
    logger.debug("Saved item to S3: $fullS3Key")

    // 3. Create pointer item (keep keys + s3Key, remove large fields)
    val pointerItem = createPointerItem(item, fullS3Key, tableInfo)

    // 4. Save pointer to DynamoDB (replaces full item)
    if (table is InlineView<*, *>) {
      val saveMethod = table::class.java.methods.find { it.name == "save" && it.parameterCount >= 1 }
      saveMethod?.invoke(table, pointerItem)
      logger.debug("Replaced DynamoDB item with pointer")
    }
  }

  private fun createPointerItem(
    fullItem: Any,
    s3Key: String,
    tableInfo: HybridTableInfo
  ): Any {
    // Create a new instance with only essential fields
    val itemClass = fullItem::class
    val constructor = itemClass.constructors.firstOrNull()
      ?: throw IllegalStateException("No constructor found for ${itemClass.simpleName}")

    val params = constructor.parameters.associateWith { param ->
      when (param.name) {
        "s3Key" -> s3Key
        "archivedAt" -> Instant.now()
        else -> {
          // Keep key fields and timestamps, null out data fields
          val property = itemClass.memberProperties.find { it.name == param.name }
          property?.isAccessible = true

          if (isKeyField(param.name) || isTimestampField(property)) {
            (property as? KProperty1<Any, *>)?.get(fullItem)
          } else {
            null // Clear large data fields
          }
        }
      }
    }

    return constructor.callBy(params)
  }

  private fun isKeyField(name: String?): Boolean {
    if (name == null) return false
    return name.contains("id", ignoreCase = true) ||
           name.contains("key", ignoreCase = true) ||
           name.contains("pk", ignoreCase = true) ||
           name.contains("sk", ignoreCase = true)
  }

  private fun isTimestampField(property: KProperty1<*, *>?): Boolean {
    if (property == null) return false
    return property.findAnnotation<ArchivalTimestamp>() != null ||
           property.name.contains("timestamp", ignoreCase = true) ||
           property.name.contains("createdAt", ignoreCase = true) ||
           property.name.contains("updatedAt", ignoreCase = true)
  }

  private fun compressGzip(data: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { gzipOutput ->
      gzipOutput.write(data)
    }
    return outputStream.toByteArray()
  }

  data class ArchiveItem(
    val item: Any,
    val archived: Boolean,
    val error: String? = null,
    val dryRun: Boolean = false
  )
}