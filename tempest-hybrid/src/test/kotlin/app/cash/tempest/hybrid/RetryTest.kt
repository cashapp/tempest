package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.io.IOException
import java.net.ConnectException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import com.amazonaws.AmazonServiceException

class RetryTest {

  private val delegate = mock<DynamoDbClient>()
  private val s3Client = mock<AmazonS3>()
  private val objectMapper = ObjectMapper()

  @Test
  fun `retries on transient S3 failures when enabled`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(
        enabled = true,
        maxAttempts = 3,
        initialDelayMs = 10 // Short delay for tests
      )
    )

    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, config)
    var attempts = 0

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to fail twice then succeed
    whenever(s3Client.getObject(any<String>(), any<String>())).thenAnswer {
      attempts++
      if (attempts < 3) {
        throw SocketTimeoutException("Timeout")
      }
      // Success on third attempt
      mockS3Object("""{"pk": {"S": "USER#123"}, "sk": {"S": "PROFILE"}, "name": {"S": "Test User"}}""")
    }

    // When
    val result = client.getItem(GetItemRequest.builder().tableName("users").build())

    // Then
    assertThat(result.hasItem()).isTrue()
    assertThat(result.item()["name"]?.s()).isEqualTo("Test User")
    assertThat(attempts).isEqualTo(3)
    verify(s3Client, times(3)).getObject(any<String>(), any<String>())
  }

  @Test
  fun `does not retry when disabled`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(enabled = false)
    )

    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, config)

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to always fail
    whenever(s3Client.getObject(any<String>(), any<String>())).thenAnswer {
      throw SocketTimeoutException("Timeout")
    }

    // When/Then - Should fail immediately
    assertThrows<S3HydrationException> {
      client.getItem(GetItemRequest.builder().tableName("users").build())
    }

    // Verify only called once
    verify(s3Client, times(1)).getObject(any<String>(), any<String>())
  }

  @Test
  fun `respects max attempts configuration`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(
        enabled = true,
        maxAttempts = 2,
        initialDelayMs = 10
      )
    )

    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, config)
    var attempts = 0

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to always fail
    whenever(s3Client.getObject(any<String>(), any<String>())).thenAnswer {
      attempts++
      throw SocketTimeoutException("Timeout")
    }

    // When/Then - Should fail after max attempts
    assertThrows<S3HydrationException> {
      client.getItem(GetItemRequest.builder().tableName("users").build())
    }

    // Verify called exactly maxAttempts times
    assertThat(attempts).isEqualTo(2)
    verify(s3Client, times(2)).getObject(any<String>(), any<String>())
  }

  @Test
  fun `applies exponential backoff with jitter`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(
        enabled = true,
        maxAttempts = 4,
        initialDelayMs = 100,
        maxDelayMs = 1000
      )
    )

    val metrics = CountingMetrics()
    val configWithMetrics = config.copy(metrics = metrics)
    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, configWithMetrics)

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to fail several times then succeed
    var attempts = 0
    whenever(s3Client.getObject(any<String>(), any<String>())).thenAnswer {
      attempts++
      if (attempts < 4) {
        throw SocketTimeoutException("Timeout")
      }
      mockS3Object("""{"pk": {"S": "USER#123"}, "sk": {"S": "PROFILE"}, "name": {"S": "Test User"}}""")
    }

    // When
    val startTime = System.currentTimeMillis()
    val result = client.getItem(GetItemRequest.builder().tableName("users").build())
    val endTime = System.currentTimeMillis()
    val totalTime = endTime - startTime

    // Then
    assertThat(result.hasItem()).isTrue()

    // Check that delays were applied (should have at least some delay from retries)
    assertThat(totalTime).isGreaterThan(100) // At least initial delay

    // Check metrics for retry attempts
    val retryEvents = metrics.events.filterIsInstance<MetricEvent.RetryAttempt>()
    assertThat(retryEvents).hasSize(4) // 3 failed attempts + 1 successful (attempts 1, 2, 3, 4)

    // Verify delays are increasing (exponential backoff) - check only the failed attempts
    val delays = retryEvents.filter { !it.succeeded }.map { it.delayMs }
    delays.zipWithNext().forEach { (prev, next) ->
      assertThat(next).isGreaterThan(prev) // Each delay should be larger than the previous
    }

    // Verify delays respect max delay
    retryEvents.forEach { event ->
      if (!event.succeeded) {
        assertThat(event.delayMs).isLessThanOrEqualTo(1000)
      }
    }
  }

  @Test
  fun `does not retry non-retryable exceptions`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(
        enabled = true,
        maxAttempts = 3,
        initialDelayMs = 10,
        retryableExceptions = setOf(
          IOException::class.java,
          SocketTimeoutException::class.java,
          ConnectException::class.java
        )
      )
    )

    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, config)

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to throw a non-retryable exception (AccessDenied)
    val accessDeniedException = AmazonServiceException("Access Denied")
    accessDeniedException.statusCode = 403
    whenever(s3Client.getObject(any<String>(), any<String>()))
      .thenThrow(accessDeniedException)

    // When/Then - Should fail immediately without retry
    assertThrows<S3HydrationException> {
      client.getItem(GetItemRequest.builder().tableName("users").build())
    }

    // Verify only called once (no retries)
    verify(s3Client, times(1)).getObject(any<String>(), any<String>())
  }

  @Test
  fun `retry succeeds on first retry`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(
        enabled = true,
        maxAttempts = 3,
        initialDelayMs = 10
      )
    )

    val metrics = CountingMetrics()
    val configWithMetrics = config.copy(metrics = metrics)
    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, configWithMetrics)

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to fail once then succeed
    var attempts = 0
    whenever(s3Client.getObject(any<String>(), any<String>())).thenAnswer {
      attempts++
      if (attempts == 1) {
        throw ConnectException("Connection failed")
      }
      mockS3Object("""{"pk": {"S": "USER#123"}, "sk": {"S": "PROFILE"}, "name": {"S": "Test User"}}""")
    }

    // When
    val result = client.getItem(GetItemRequest.builder().tableName("users").build())

    // Then
    assertThat(result.hasItem()).isTrue()
    assertThat(result.item()["name"]?.s()).isEqualTo("Test User")
    assertThat(attempts).isEqualTo(2)

    // Check metrics - should have 2 events: one for the failed first attempt, one for the successful second attempt
    val retryEvents = metrics.events.filterIsInstance<MetricEvent.RetryAttempt>()
    assertThat(retryEvents).hasSize(2)
    assertThat(retryEvents[0].attempt).isEqualTo(1)
    assertThat(retryEvents[0].succeeded).isFalse()
    assertThat(retryEvents[1].attempt).isEqualTo(2)
    assertThat(retryEvents[1].succeeded).isTrue()
  }

  @Test
  fun `retry works with different retryable exceptions`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      retryConfig = HybridConfig.RetryConfig(
        enabled = true,
        maxAttempts = 4,
        initialDelayMs = 10,
        retryableExceptions = setOf(
          IOException::class.java,
          SocketTimeoutException::class.java,
          ConnectException::class.java
        )
      )
    )

    val client = S3AwareDynamoDbClient(delegate, s3Client, objectMapper, config)

    // Setup DynamoDB to return an S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://users/123/profile.json").build()
    )
    whenever(delegate.getItem(any<GetItemRequest>())).thenReturn(
      GetItemResponse.builder().item(pointerItem).build()
    )

    // Setup S3 to fail with different retryable exceptions then succeed
    var attempts = 0
    whenever(s3Client.getObject(any<String>(), any<String>())).thenAnswer {
      attempts++
      when (attempts) {
        1 -> throw IOException("IO error")
        2 -> throw SocketTimeoutException("Timeout")
        3 -> throw ConnectException("Connection failed")
        else -> mockS3Object("""{"pk": {"S": "USER#123"}, "sk": {"S": "PROFILE"}, "name": {"S": "Test User"}}""")
      }
    }

    // When
    val result = client.getItem(GetItemRequest.builder().tableName("users").build())

    // Then
    assertThat(result.hasItem()).isTrue()
    assertThat(result.item()["name"]?.s()).isEqualTo("Test User")
    assertThat(attempts).isEqualTo(4)
    verify(s3Client, times(4)).getObject(any<String>(), any<String>())
  }

  private fun mockS3Object(jsonContent: String): S3Object {
    val s3Object = mock<S3Object>()
    val inputStream = S3ObjectInputStream(ByteArrayInputStream(jsonContent.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(inputStream)
    return s3Object
  }
}