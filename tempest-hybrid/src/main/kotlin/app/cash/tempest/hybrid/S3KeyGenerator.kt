package app.cash.tempest.hybrid

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Generates S3 keys based on item data and template
 */
internal object S3KeyGenerator {
  
  fun generateS3Key(
    item: Any,
    template: String,
    tableName: String
  ): String {
    
    val partitionKey = extractPartitionKey(item)
    val sortKey = extractSortKey(item)
    
    return template
      .replace("{table}", tableName)
      .replace("{pk}", partitionKey)
      .replace("{sk}", sortKey ?: "")
      .replace("//", "/") // Clean up double slashes
  }
  
  private fun extractPartitionKey(item: Any): String {
    val properties = item::class.memberProperties
    
    // Look for userId property first (common pattern)
    val pkProperty = properties.find { prop ->
      prop.name.equals("userId", ignoreCase = true) ||
      prop.name.contains("partition", ignoreCase = true) ||
      prop.name.contains("pk", ignoreCase = true)
    } ?: properties.firstOrNull()
    
    return pkProperty?.let { prop ->
      prop.isAccessible = true
      (prop as KProperty1<Any, *>).get(item)?.toString()
    } ?: "unknown"
  }
  
  private fun extractSortKey(item: Any): String? {
    val properties = item::class.memberProperties
    
    // Look for sessionId property first (common pattern)
    val skProperty = properties.find { prop ->
      prop.name.equals("sessionId", ignoreCase = true) ||
      prop.name.contains("sort", ignoreCase = true) ||
      prop.name.contains("sk", ignoreCase = true)
    }
    
    return skProperty?.let { prop ->
      prop.isAccessible = true
      (prop as KProperty1<Any, *>).get(item)?.toString()
    }
  }
}
