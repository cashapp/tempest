package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
 * Factory for creating S3-aware DynamoDB components.
 *
 * This is the main entry point for enabling hybrid storage. It provides methods to wrap existing DynamoDB clients
 * with S3-aware implementations that handle pointer items transparently.
 *
 * @param s3Client The S3 client for fetching stored objects
 * @param objectMapper The JSON object mapper for deserializing S3 data
 * @param hybridConfig Configuration for S3 storage
 * @param s3Executor Optional executor for parallel S3 reads. If null, reads will be sequential.
 *                   The caller is responsible for managing the lifecycle of this executor.
 */
class HybridDynamoDbFactory(
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig,
  private val s3Executor: Executor? = null,
) {

  companion object {
    private val logger = LoggerFactory.getLogger(HybridDynamoDbFactory::class.java)

    /**
     * Creates a properly configured executor for S3 hydration with automatic shutdown on JVM exit.
     *
     * This is a convenience method that creates an executor with:
     * - Daemon threads (won't prevent JVM shutdown)
     * - Automatic shutdown hook
     * - Descriptive thread names
     *
     * @param threadCount Number of threads for parallel S3 fetching
     * @param threadNamePrefix Prefix for thread names (default: "s3-hydration")
     * @return An ExecutorService configured for S3 hydration
     */
    @JvmStatic
    @JvmOverloads
    fun createS3Executor(
      threadCount: Int = 10,
      threadNamePrefix: String = "s3-hydration"
    ): ExecutorService {
      require(threadCount > 0) { "Thread count must be positive" }

      val executor = Executors.newFixedThreadPool(threadCount, object : ThreadFactory {
        private var counter = 0
        override fun newThread(r: Runnable): Thread {
          return Thread(r, "$threadNamePrefix-${counter++}").apply {
            isDaemon = true // Daemon threads won't prevent JVM shutdown
          }
        }
      })

      // Add shutdown hook to clean up the executor
      Runtime.getRuntime().addShutdownHook(Thread {
        if (!executor.isShutdown) {
          logger.debug("Shutting down S3 executor")
          executor.shutdown()
          try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
              logger.debug("Forcing S3 executor shutdown")
              executor.shutdownNow()
            }
          } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
          }
        }
      })

      logger.debug("Created S3 executor with $threadCount threads")
      return executor
    }
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
    logger.debug("Creating S3-aware DynamoDB client wrapper")
    return S3AwareDynamoDbClient(
      delegate = client,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = hybridConfig,
      s3Executor = s3Executor,
    )
  }

}
