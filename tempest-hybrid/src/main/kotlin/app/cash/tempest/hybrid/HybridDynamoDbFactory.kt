package app.cash.tempest.hybrid

import app.cash.tempest2.LogicalDb
import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
 * Factory for creating S3-aware DynamoDB components.
 *
 * This is the main entry point for enabling hybrid storage. It provides methods to wrap existing DynamoDB clients and
 * LogicalDbs with S3-aware implementations that handle pointer items transparently.
 */
class HybridDynamoDbFactory(
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig,
) {

  companion object {
    private val logger = LoggerFactory.getLogger(HybridDynamoDbFactory::class.java)
  }

  /**
   * Wraps a DynamoDbClient with S3-aware functionality.
   *
   * This wrapper intercepts query, scan, and getItem operations to detect and hydrate S3 pointer items before they
   * reach Tempest.
   *
   * S3 pointer items can be minimal, containing only:
   * - pk (partition key)
   * - sk (sort key)
   * - _s3_pointer (S3 location in format s3://bucket/key)
   *
   * The wrapper will automatically load the full data from S3 and replace the pointer item with the complete item.
   */
  fun wrapDynamoDbClient(client: DynamoDbClient): DynamoDbClient {
    logger.info("🔧 Creating S3-aware DynamoDB client wrapper")
    return S3AwareDynamoDbClient(
      delegate = client,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = hybridConfig,
    )
  }

  /**
   * Wraps a LogicalDb with S3-aware functionality.
   *
   * This wrapper ensures that tables created from the LogicalDb use S3-aware codecs that can handle pointer items.
   */
  fun wrapLogicalDb(logicalDb: LogicalDb): LogicalDb {
    logger.info("🔧 Creating S3-aware LogicalDb wrapper")
    return S3AwareLogicalDb(
      delegate = logicalDb,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = hybridConfig,
    )
  }
}
