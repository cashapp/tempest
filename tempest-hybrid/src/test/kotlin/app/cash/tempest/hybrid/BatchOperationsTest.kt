package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class BatchOperationsTest {

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
  fun `batchGetItem with S3 pointers hydrates correctly`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket")
    )

    val executor = Executors.newFixedThreadPool(2)
    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = executor
    )

    // Setup S3 mock data
    val s3Data1 = """{"pk": {"S": "USER#1"}, "sk": {"S": "DATA#1"}, "value": {"S": "hydrated-1"}}"""
    val s3Data2 = """{"pk": {"S": "USER#2"}, "sk": {"S": "DATA#2"}, "value": {"S": "hydrated-2"}}"""

    val s3Object1 = createMockS3Object(s3Data1)
    val s3Object2 = createMockS3Object(s3Data2)

    whenever(s3Client.getObject("test-bucket", "key1")).thenReturn(s3Object1)
    whenever(s3Client.getObject("test-bucket", "key2")).thenReturn(s3Object2)

    // Setup DynamoDB mock response with S3 pointers
    val table1Items = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("USER#1").build(),
        "sk" to AttributeValue.builder().s("DATA#1").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://key1").build()
      ),
      mapOf(
        "pk" to AttributeValue.builder().s("REGULAR#1").build(),
        "sk" to AttributeValue.builder().s("DATA#1").build(),
        "value" to AttributeValue.builder().s("regular-value").build()
      )
    )

    val table2Items = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("USER#2").build(),
        "sk" to AttributeValue.builder().s("DATA#2").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://key2").build()
      )
    )

    val batchResponse = BatchGetItemResponse.builder()
      .responses(mapOf(
        "table1" to table1Items,
        "table2" to table2Items
      ))
      .build()

    val batchRequest = BatchGetItemRequest.builder()
      .requestItems(mapOf())
      .build()

    whenever(delegateClient.batchGetItem(batchRequest)).thenReturn(batchResponse)

    // When
    val result = client.batchGetItem(batchRequest)

    // Then
    assertThat(result.responses()).containsKeys("table1", "table2")

    val table1Results = result.responses()["table1"]!!
    assertThat(table1Results).hasSize(2)
    // First item should be hydrated from S3
    assertThat(table1Results[0]["value"]?.s()).isEqualTo("hydrated-1")
    assertThat(table1Results[0]["_s3_pointer"]?.s()).isEqualTo("s3://key1")
    // Second item should be unchanged (no S3 pointer)
    assertThat(table1Results[1]["value"]?.s()).isEqualTo("regular-value")

    val table2Results = result.responses()["table2"]!!
    assertThat(table2Results).hasSize(1)
    assertThat(table2Results[0]["value"]?.s()).isEqualTo("hydrated-2")

    // Verify S3 was called for both pointers
    verify(s3Client).getObject("test-bucket", "key1")
    verify(s3Client).getObject("test-bucket", "key2")

    executor.shutdown()
  }

  @Test
  fun `batchGetItem without S3 pointers returns original response`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket")
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup DynamoDB mock response without S3 pointers
    val items = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("USER#1").build(),
        "sk" to AttributeValue.builder().s("DATA#1").build(),
        "value" to AttributeValue.builder().s("regular-value").build()
      )
    )

    val batchResponse = BatchGetItemResponse.builder()
      .responses(mapOf("table1" to items))
      .build()

    val batchRequest = BatchGetItemRequest.builder()
      .requestItems(mapOf())
      .build()

    whenever(delegateClient.batchGetItem(batchRequest)).thenReturn(batchResponse)

    // When
    val result = client.batchGetItem(batchRequest)

    // Then
    assertThat(result).isSameAs(batchResponse) // Should return original response
    verify(s3Client, never()).getObject(any<String>(), any<String>())
  }

  @Test
  fun `transactGetItems with S3 pointers hydrates correctly`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket")
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup S3 mock data
    val s3Data = """{"pk": {"S": "USER#1"}, "sk": {"S": "DATA#1"}, "value": {"S": "hydrated-from-s3"}}"""
    val s3Object = createMockS3Object(s3Data)
    whenever(s3Client.getObject("test-bucket", "transact-key")).thenReturn(s3Object)

    // Setup DynamoDB mock response with mixed items
    val itemResponses = listOf(
      ItemResponse.builder()
        .item(mapOf(
          "pk" to AttributeValue.builder().s("USER#1").build(),
          "sk" to AttributeValue.builder().s("DATA#1").build(),
          "_s3_pointer" to AttributeValue.builder().s("s3://transact-key").build()
        ))
        .build(),
      ItemResponse.builder()
        .item(mapOf(
          "pk" to AttributeValue.builder().s("USER#2").build(),
          "sk" to AttributeValue.builder().s("DATA#2").build(),
          "value" to AttributeValue.builder().s("regular-value").build()
        ))
        .build(),
      ItemResponse.builder()
        .item(null) // Null item (not found)
        .build()
    )

    val transactResponse = TransactGetItemsResponse.builder()
      .responses(itemResponses)
      .build()

    val transactRequest = TransactGetItemsRequest.builder()
      .transactItems(listOf())
      .build()

    whenever(delegateClient.transactGetItems(transactRequest)).thenReturn(transactResponse)

    // When
    val result = client.transactGetItems(transactRequest)

    // Then
    assertThat(result.responses()).hasSize(3)

    // First item should be hydrated from S3
    val firstItem = result.responses()[0].item()
    assertThat(firstItem).isNotNull
    assertThat(firstItem!!["value"]?.s()).isEqualTo("hydrated-from-s3")
    assertThat(firstItem["_s3_pointer"]?.s()).isEqualTo("s3://transact-key")

    // Second item should be unchanged
    val secondItem = result.responses()[1].item()
    assertThat(secondItem).isNotNull
    assertThat(secondItem!!["value"]?.s()).isEqualTo("regular-value")

    // Third item should remain null or empty (SDK might return empty map instead of null)
    val thirdItem = result.responses()[2].item()
    assertThat(thirdItem == null || thirdItem.isEmpty()).isTrue()

    // Verify S3 was called only for the pointer item
    verify(s3Client).getObject("test-bucket", "transact-key")
  }

  @Test
  fun `transactGetItems without S3 pointers returns original response`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket")
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config,
      s3Executor = null
    )

    // Setup DynamoDB mock response without S3 pointers
    val itemResponses = listOf(
      ItemResponse.builder()
        .item(mapOf(
          "pk" to AttributeValue.builder().s("USER#1").build(),
          "value" to AttributeValue.builder().s("regular-value").build()
        ))
        .build()
    )

    val transactResponse = TransactGetItemsResponse.builder()
      .responses(itemResponses)
      .build()

    val transactRequest = TransactGetItemsRequest.builder()
      .transactItems(listOf())
      .build()

    whenever(delegateClient.transactGetItems(transactRequest)).thenReturn(transactResponse)

    // When
    val result = client.transactGetItems(transactRequest)

    // Then
    assertThat(result).isSameAs(transactResponse) // Should return original response
    verify(s3Client, never()).getObject(any<String>(), any<String>())
  }

  private fun createMockS3Object(content: String): S3Object {
    val s3Object = mock<S3Object>()
    val inputStream = S3ObjectInputStream(
      ByteArrayInputStream(content.toByteArray()),
      null
    )
    whenever(s3Object.objectContent).thenReturn(inputStream)
    return s3Object
  }
}