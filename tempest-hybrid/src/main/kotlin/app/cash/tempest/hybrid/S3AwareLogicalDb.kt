package app.cash.tempest.hybrid

import app.cash.tempest2.LogicalDb
import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * A LogicalDb wrapper for future S3-aware functionality.
 *
 * Currently, the main S3 pointer handling is done at the DynamoDbClient level via S3AwareDynamoDbClient. This wrapper
 * exists for potential future extensions.
 */
class S3AwareLogicalDb(
  private val delegate: LogicalDb,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig,
) : LogicalDb by delegate {

  companion object {
    private val logger = LoggerFactory.getLogger(S3AwareLogicalDb::class.java)
  }

  init {
    logger.info("🔧 S3AwareLogicalDb wrapper created")
    logger.info("  S3 bucket: ${hybridConfig.s3Config.bucketName}")
    logger.info("  S3 region: ${hybridConfig.s3Config.region}")
  }
}
