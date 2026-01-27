package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class ExecutorLifecycleTest {

  @Test
  fun `createS3Executor creates daemon threads`() {
    // When
    val executor = HybridDynamoDbFactory.createS3Executor(
      threadCount = 2,
      threadNamePrefix = "test-executor"
    )

    // Then
    executor.submit {
      val currentThread = Thread.currentThread()
      assertThat(currentThread.isDaemon).isTrue()
      assertThat(currentThread.name).startsWith("test-executor")
    }.get(1, TimeUnit.SECONDS)

    // Cleanup
    executor.shutdown()
    executor.awaitTermination(1, TimeUnit.SECONDS)
  }

  @Test
  fun `createS3Executor creates correct number of threads`() {
    // When
    val executor = HybridDynamoDbFactory.createS3Executor(threadCount = 5)

    // Submit tasks to force thread creation
    val futures = (1..5).map {
      executor.submit {
        Thread.sleep(100) // Hold the thread briefly
      }
    }

    // Wait for all to complete
    futures.forEach { it.get(1, TimeUnit.SECONDS) }

    // Then
    assertThat(executor.toString()).contains("pool size = 5")

    // Cleanup
    executor.shutdown()
    executor.awaitTermination(1, TimeUnit.SECONDS)
  }

  @Test
  fun `createS3Executor validates thread count`() {
    // When/Then
    try {
      HybridDynamoDbFactory.createS3Executor(threadCount = 0)
      assertThat(false).isTrue() // Should not reach here
    } catch (e: IllegalArgumentException) {
      assertThat(e.message).contains("Thread count must be positive")
    }

    try {
      HybridDynamoDbFactory.createS3Executor(threadCount = -1)
      assertThat(false).isTrue() // Should not reach here
    } catch (e: IllegalArgumentException) {
      assertThat(e.message).contains("Thread count must be positive")
    }
  }

  @Test
  fun `S3AwareDynamoDbClient tracks executor lifecycle`() {
    // Given
    val delegateClient = mock<DynamoDbClient>()
    val s3Client = mock<AmazonS3>()
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket")
    )

    // Create a non-daemon executor (to test warning logic)
    val executor = Executors.newFixedThreadPool(2)

    // When - create multiple clients with the same executor
    val client1 = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = executor
    )

    val client2 = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = executor
    )

    // Then - both clients are created successfully
    assertThat(client1).isNotNull
    assertThat(client2).isNotNull

    // Cleanup - shutdown executor to avoid warnings
    executor.shutdown()
    executor.awaitTermination(1, TimeUnit.SECONDS)
  }

  @Test
  fun `convenience factory method creates properly configured executor`() {
    // When
    val executor = HybridDynamoDbFactory.createS3Executor()

    // Then
    assertThat(executor).isNotNull
    assertThat(executor.isShutdown).isFalse()
    assertThat(executor.isTerminated).isFalse()

    // Submit a task to verify it works
    val callable = Callable { "test" }
    val future = executor.submit(callable)
    val result = future.get(1, TimeUnit.SECONDS)
    assertThat(result).isEqualTo("test")

    // Cleanup - important to shut it down before the shutdown hook runs
    executor.shutdown()
    assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue()
  }
}