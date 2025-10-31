package app.cash.tempest.hybrid

import java.time.Duration

/**
 * Configuration for pointer-based hybrid storage
 */
data class HybridConfig(
  val s3Config: S3Config,
  val archivalConfig: ArchivalConfig
) {
  
  data class S3Config(
    val bucketName: String,
    val region: String,
    val keyPrefix: String = "",
    val accessKey: String? = null,
    val secretKey: String? = null
  )
  
  data class ArchivalConfig(
    val enabled: Boolean = true,
    val archiveAfterDuration: Duration = Duration.ofDays(365),
    val deleteFromDynamoAfterArchival: Boolean = true
  )
}

/**
 * Annotation to mark a table for hybrid storage
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HybridTable(
  val archiveAfterDays: Int = 365,
  val s3KeyTemplate: String = "{table}/{pk}/{sk}.json"
)

/**
 * Annotation to mark the field used for archival age determination
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ArchivalTimestamp
