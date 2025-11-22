/*
 * Copyright 2024 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.tempest.hybrid

import app.cash.tempest2.Attribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Generates deterministic S3 keys based solely on DynamoDB keys.
 *
 * IMPORTANT: S3 keys are stored as pointers in DynamoDB and must be recreatable from the item's keys alone. Never
 * include timestamps or mutable fields.
 */
object S3KeyGenerator {

  fun generateS3Key(item: Any, template: String, tableName: String): String {

    val (partitionKey, sortKey) = extractKeys(item)

    // Sanitize keys to be S3-safe (remove special characters that could cause issues)
    val safePartitionKey = sanitizeForS3(partitionKey)
    val safeSortKey = sortKey?.let { sanitizeForS3(it) } ?: ""

    val s3Key =
      template
        .replace("{tableName}", tableName)
        .replace("{partitionKey}", safePartitionKey)
        .replace("{sortKey}", safeSortKey)
        // Legacy support for abbreviated variables
        .replace("{table}", tableName)
        .replace("{pk}", safePartitionKey)
        .replace("{sk}", safeSortKey)
        // Clean up any double slashes or trailing slashes
        .replace(Regex("/+"), "/")
        .trimEnd('/')

    // Add .json.gz extension if not present
    return if (s3Key.endsWith(".json.gz") || s3Key.endsWith(".json")) {
      s3Key
    } else {
      "$s3Key.json.gz"
    }
  }

  /**
   * Sanitize a string to be safe for use in S3 keys. S3 keys can contain most characters, but we want to avoid issues
   * with:
   * - URL encoding problems
   * - File system incompatibilities
   * - Special characters that might cause issues
   */
  private fun sanitizeForS3(value: String): String {
    return value
      // Replace problematic characters with underscores
      .replace(Regex("[<>:\"|?*\\\\]"), "_")
      // Replace multiple underscores with single
      .replace(Regex("_+"), "_")
      // Trim underscores from ends
      .trim('_')
  }

  /** Extract partition and sort keys using annotations, not heuristics */
  fun extractKeys(item: Any): Pair<String, String?> {
    val itemClass = item::class
    val properties = itemClass.memberProperties

    // Find partition key using annotations
    val partitionKeyProp =
      properties.find { prop ->
        // Check for DynamoDB annotations
        prop.findAnnotation<DynamoDBHashKey>() != null ||
          // Check for Tempest Attribute annotation with partition_key name
          prop.findAnnotation<Attribute>()?.name == "partition_key"
      }

    // Find sort key using annotations
    val sortKeyProp =
      properties.find { prop ->
        // Check for DynamoDB annotations
        prop.findAnnotation<DynamoDBRangeKey>() != null ||
          // Check for Tempest Attribute annotation with sort_key name
          prop.findAnnotation<Attribute>()?.name == "sort_key"
      }

    // Extract values
    val partitionKey =
      partitionKeyProp?.let { prop ->
        prop.isAccessible = true
        (prop as KProperty1<Any, *>).get(item)?.toString()
      }
        ?: throw IllegalStateException(
          "Could not find partition key for item of type ${itemClass.simpleName}. " +
            "Ensure field has @DynamoDBHashKey or @Attribute(name=\"partition_key\")"
        )

    val sortKey =
      sortKeyProp?.let { prop ->
        prop.isAccessible = true
        (prop as KProperty1<Any, *>).get(item)?.toString()
      }

    return partitionKey to sortKey
  }

  /** Check if a property is a DynamoDB key field */
  fun isKeyField(property: KProperty1<*, *>): Boolean {
    return property.findAnnotation<DynamoDBHashKey>() != null ||
      property.findAnnotation<DynamoDBRangeKey>() != null ||
      property.findAnnotation<Attribute>()?.name in listOf("partition_key", "sort_key")
  }
}
