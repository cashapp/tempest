package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class ErrorHandlingTest {

  private lateinit var delegateClient: DynamoDbClient
  private lateinit var s3Client: AmazonS3
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setUp() {
    delegateClient = mock()
    s3Client = mock()
    objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
  }

  @Test
  fun `FAIL_FAST strategy throws exception on S3 failure`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.FAIL_FAST
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 to fail with RuntimeException (getObject doesn't declare IOException)
    whenever(s3Client.getObject(any<String>(), any<String>()))
      .thenThrow(RuntimeException("S3 connection failed"))

    // Setup DynamoDB response with S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://test-key").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    val queryRequest = QueryRequest.builder().tableName("test").build()
    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // When/Then
    assertThatThrownBy { client.query(queryRequest) }
      .isInstanceOf(S3HydrationException::class.java)
      .hasMessageContaining("Failed to hydrate S3 pointer: s3://test-key")
      .hasCauseInstanceOf(RuntimeException::class.java)

    // Also check the s3Pointer field
    assertThatThrownBy { client.query(queryRequest) }
      .isInstanceOfSatisfying(S3HydrationException::class.java) { exception ->
        assertThat(exception.s3Pointer).isEqualTo("s3://test-key")
      }
  }

  @Test
  fun `RETURN_POINTER strategy returns pointer item on S3 failure`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.RETURN_POINTER
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 to fail
    val s3Exception = AmazonS3Exception("Access Denied")
    s3Exception.statusCode = 403
    whenever(s3Client.getObject(any<String>(), any<String>())).thenThrow(s3Exception)

    // Setup DynamoDB response with S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("DATA#456").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://test-key").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    val queryRequest = QueryRequest.builder().tableName("test").build()
    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // When
    val result = client.query(queryRequest)

    // Then - should return the original pointer item
    assertThat(result.items()).hasSize(1)
    assertThat(result.items()[0]).isEqualTo(pointerItem)
    assertThat(result.items()[0]["_s3_pointer"]?.s()).isEqualTo("s3://test-key")
    // Should NOT have the hydrated fields
    assertThat(result.items()[0]).doesNotContainKey("value")
  }

  @Test
  fun `SKIP_FAILED strategy excludes failed items from results`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.SKIP_FAILED
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 to fail for one item, succeed for another
    val successData = """{"pk": {"S": "USER#2"}, "sk": {"S": "DATA#2"}, "value": {"S": "success"}}"""
    val successS3Object = createMockS3Object(successData)

    whenever(s3Client.getObject("test-bucket", "fail-key"))
      .thenThrow(RuntimeException("S3 failure"))
    whenever(s3Client.getObject("test-bucket", "success-key"))
      .thenReturn(successS3Object)

    // Setup DynamoDB response with mixed items
    val items = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("USER#1").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://fail-key").build()
      ),
      mapOf(
        "pk" to AttributeValue.builder().s("USER#2").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://success-key").build()
      ),
      mapOf(
        "pk" to AttributeValue.builder().s("USER#3").build(),
        "value" to AttributeValue.builder().s("regular").build()
      )
    )

    val queryResponse = QueryResponse.builder().items(items).build()
    val queryRequest = QueryRequest.builder().tableName("test").build()
    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // When
    val result = client.query(queryRequest)

    // Then - should only have 2 items (failed one is skipped)
    assertThat(result.items()).hasSize(2)
    assertThat(result.items()[0]["pk"]?.s()).isEqualTo("USER#2")
    assertThat(result.items()[0]["value"]?.s()).isEqualTo("success")
    assertThat(result.items()[1]["pk"]?.s()).isEqualTo("USER#3")
    assertThat(result.items()[1]["value"]?.s()).isEqualTo("regular")
  }

  @Test
  fun `getItem with FAIL_FAST throws on S3 failure`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.FAIL_FAST
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 to fail
    whenever(s3Client.getObject(any<String>(), any<String>()))
      .thenThrow(RuntimeException("Network error"))

    // Setup DynamoDB response with S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://item-key").build()
    )

    val getItemResponse = GetItemResponse.builder().item(pointerItem).build()
    val getItemRequest = GetItemRequest.builder().tableName("test").build()
    whenever(delegateClient.getItem(getItemRequest)).thenReturn(getItemResponse)

    // When/Then
    assertThatThrownBy { client.getItem(getItemRequest) }
      .isInstanceOf(S3HydrationException::class.java)
      .hasMessageContaining("s3://item-key")
  }

  @Test
  fun `getItem with SKIP_FAILED returns empty on S3 failure`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.SKIP_FAILED
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 to fail
    whenever(s3Client.getObject(any<String>(), any<String>()))
      .thenThrow(RuntimeException("S3 error"))

    // Setup DynamoDB response with S3 pointer
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://item-key").build()
    )

    val getItemResponse = GetItemResponse.builder().item(pointerItem).build()
    val getItemRequest = GetItemRequest.builder().tableName("test").build()
    whenever(delegateClient.getItem(getItemRequest)).thenReturn(getItemResponse)

    // When
    val result = client.getItem(getItemRequest)

    // Then - should return empty item
    assertThat(result.hasItem()).isTrue()
    assertThat(result.item()).isEmpty()
  }

  @Test
  fun `error strategy applies to batch operations`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.FAIL_FAST
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 to fail
    whenever(s3Client.getObject(any<String>(), any<String>()))
      .thenThrow(RuntimeException("Batch S3 failure"))

    // Setup batch response with S3 pointer
    val items = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("BATCH#1").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://batch-key").build()
      )
    )

    val batchResponse = BatchGetItemResponse.builder()
      .responses(mapOf("table1" to items))
      .build()

    val batchRequest = BatchGetItemRequest.builder().build()
    whenever(delegateClient.batchGetItem(batchRequest)).thenReturn(batchResponse)

    // When/Then
    assertThatThrownBy { client.batchGetItem(batchRequest) }
      .isInstanceOf(S3HydrationException::class.java)
      .hasMessageContaining("s3://batch-key")
  }

  private fun createMockS3Object(content: String): com.amazonaws.services.s3.model.S3Object {
    val s3Object = mock<com.amazonaws.services.s3.model.S3Object>()
    val inputStream = com.amazonaws.services.s3.model.S3ObjectInputStream(
      java.io.ByteArrayInputStream(content.toByteArray()),
      null
    )
    whenever(s3Object.objectContent).thenReturn(inputStream)
    return s3Object
  }
}