package app.cash.tempest.hybrid

import app.cash.tempest.InlineView
import app.cash.tempest.Page
import app.cash.tempest.KeyCondition
import app.cash.tempest.QueryConfig
import app.cash.tempest.ScanConfig
import app.cash.tempest.Offset
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.GetObjectRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

internal class HybridViewProxy(
  private val regularView: InlineView<*, *>,
  private val hybridAnnotation: HybridTable,
  private val itemClass: Class<*>,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig
) : InvocationHandler {

  companion object {
    private val logger = LoggerFactory.getLogger(HybridViewProxy::class.java)
    private val executor = Executors.newFixedThreadPool(10) // For parallel S3 loads
  }

  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {

    when (method.name) {
      "loadHybrid" -> {
        val key = args?.firstOrNull()
        return if (key != null) {
          loadHybrid(key)
        } else null
      }
      "load" -> {
        // Override regular load to use hybrid logic
        val key = args?.firstOrNull()
        return if (key != null) {
          loadHybrid(key)
        } else null
      }
      "query" -> {
        // Intercept query operations to hydrate S3 pointers
        return handleQuery(method, args)
      }
      "scan" -> {
        // Intercept scan operations to hydrate S3 pointers
        return handleScan(method, args)
      }
      else -> {
        // Delegate other methods to regular view
        return method.invoke(regularView, *(args ?: emptyArray()))
      }
    }
  }
  
  private fun loadHybrid(key: Any): Any? {
    // First try to load from DynamoDB
    val dynamoItem = try {
      val loadMethod = regularView.javaClass.getMethod("load", key.javaClass)
      loadMethod.invoke(regularView, key)
    } catch (e: Exception) {
      null
    }
    
    if (dynamoItem != null) {
      // Check if this is a pointer (has s3Key field) or actual data
      if (isPointer(dynamoItem)) {
        // Load actual data from S3
        return loadFromS3(dynamoItem)
      } else {
        // This is actual data in DynamoDB
        return dynamoItem
      }
    }
    
    return null
  }
  
  private fun isPointer(item: Any): Boolean {
    // Check if the item has an s3Key field indicating it's a pointer
    return try {
      val s3KeyField = item.javaClass.getDeclaredField("s3Key")
      s3KeyField.isAccessible = true
      s3KeyField.get(item) != null
    } catch (e: Exception) {
      false
    }
  }
  
  private fun loadFromS3(pointerItem: Any): Any? {
    return loadFromS3WithRetry(pointerItem, maxRetries = 3)
  }

  private fun loadFromS3WithRetry(pointerItem: Any, maxRetries: Int = 3): Any? {
    repeat(maxRetries) { attempt ->
      try {
        return loadFromS3Internal(pointerItem)
      } catch (e: AmazonS3Exception) {
        if (e.statusCode == 404) {
          logger.error("S3 object not found for pointer: ${extractS3Key(pointerItem)}")
          return pointerItem // Return pointer as fallback
        }
        if (attempt == maxRetries - 1) {
          logger.error("Failed to load from S3 after $maxRetries attempts", e)
          return pointerItem // Return pointer as fallback
        }
        Thread.sleep(100L * (attempt + 1)) // Exponential backoff
      } catch (e: Exception) {
        logger.error("Unexpected error loading from S3", e)
        return pointerItem // Return pointer as fallback
      }
    }
    return null
  }

  private fun loadFromS3Internal(pointerItem: Any): Any? {
    val s3Key = extractS3Key(pointerItem) ?: return null

    // Load from S3
    val request = GetObjectRequest(hybridConfig.s3Config.bucketName, s3Key)
    val s3Object = s3Client.getObject(request)

    val content = s3Object.objectContent.use { it.readBytes() }

    // Decompress if needed (check if it's gzipped)
    val decompressed = if (isGzipped(content)) {
      decompressGzip(content)
    } else {
      content
    }

    // Deserialize back to original item type
    return objectMapper.readValue(decompressed, itemClass)
  }

  private fun extractS3Key(item: Any): String? {
    return try {
      val s3KeyField = item.javaClass.getDeclaredField("s3Key")
      s3KeyField.isAccessible = true
      s3KeyField.get(item) as? String
    } catch (e: Exception) {
      null
    }
  }

  private fun handleQuery(method: Method, args: Array<out Any>?): Any? {
    // Execute the regular query first
    val dynamoPage = method.invoke(regularView, *(args ?: emptyArray())) as? Page<*, *> ?: return null

    // Hydrate any pointers in the results
    val hydratedContents = hydrateItems(dynamoPage.contents)

    // Return a new Page with hydrated items
    return try {
      Page::class.java.getDeclaredConstructor(
        List::class.java,
        Offset::class.java,
        Int::class.java,
        Any::class.java
      ).newInstance(
        hydratedContents,
        dynamoPage.offset,
        dynamoPage.scannedCount,
        dynamoPage.consumedCapacity
      )
    } catch (e: Exception) {
      // Fallback for different Page constructor signatures
      Page::class.java.getDeclaredConstructor(
        List::class.java,
        Offset::class.java,
        Any::class.java
      ).newInstance(
        hydratedContents,
        dynamoPage.offset,
        dynamoPage.consumedCapacity
      )
    }
  }

  private fun handleScan(method: Method, args: Array<out Any>?): Any? {
    // Execute the regular scan first
    val dynamoPage = method.invoke(regularView, *(args ?: emptyArray())) as? Page<*, *> ?: return null

    // Hydrate any pointers in the results
    val hydratedContents = hydrateItems(dynamoPage.contents)

    // Return a new Page with hydrated items
    return try {
      Page::class.java.getDeclaredConstructor(
        List::class.java,
        Offset::class.java,
        Int::class.java,
        Any::class.java
      ).newInstance(
        hydratedContents,
        dynamoPage.offset,
        dynamoPage.scannedCount,
        dynamoPage.consumedCapacity
      )
    } catch (e: Exception) {
      // Fallback for different Page constructor signatures
      Page::class.java.getDeclaredConstructor(
        List::class.java,
        Offset::class.java,
        Any::class.java
      ).newInstance(
        hydratedContents,
        dynamoPage.offset,
        dynamoPage.consumedCapacity
      )
    }
  }

  private fun hydrateItems(items: List<*>): List<Any> {
    if (items.isEmpty()) return items as List<Any>

    // Separate pointers from regular items
    val itemsWithIndex = items.mapIndexed { index, item -> index to item }
    val (pointers, regular) = itemsWithIndex.partition { (_, item) ->
      item != null && isPointer(item)
    }

    if (pointers.isEmpty()) {
      return items as List<Any> // Fast path: no S3 reads needed
    }

    // Parallel load from S3 for pointers
    val futures = pointers.map { (index, pointer) ->
      CompletableFuture.supplyAsync({
        index to loadFromS3WithRetry(pointer!!)
      }, executor)
    }

    // Wait for all futures to complete
    val hydratedResults = futures.map { it.get() }.toMap()

    // Reconstruct the list maintaining original order
    return items.mapIndexed { index, item ->
      hydratedResults[index] ?: item!!
    }
  }

  private fun isGzipped(bytes: ByteArray): Boolean {
    // Check for GZIP magic number (1f 8b)
    return bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
  }

  private fun decompressGzip(compressed: ByteArray): ByteArray {
    return GZIPInputStream(ByteArrayInputStream(compressed)).use { gzipInput ->
      gzipInput.readBytes()
    }
  }

  private fun compressGzip(data: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { gzipOutput ->
      gzipOutput.write(data)
    }
    return outputStream.toByteArray()
  }
}
