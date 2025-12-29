package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class S3AwareDynamoDbClientTest {

  private lateinit var delegateClient: DynamoDbClient
  private lateinit var s3Client: AmazonS3
  private lateinit var objectMapper: ObjectMapper
  private lateinit var hybridConfig: HybridConfig
  private lateinit var s3Executor: Executor
  private lateinit var s3AwareClient: S3AwareDynamoDbClient

  @BeforeEach
  fun setUp() {
    delegateClient = mock()
    s3Client = mock()
    objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    hybridConfig = HybridConfig(
      s3Config = HybridConfig.S3Config(
        bucketName = "test-bucket",
        keyPrefix = "test-prefix/",
        region = "us-east-1"
      ),
      errorStrategy = HybridConfig.ErrorStrategy.RETURN_POINTER // Keep old behavior for existing tests
    )
    s3Executor = Executors.newFixedThreadPool(2)

    s3AwareClient = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = hybridConfig,
      s3Executor = s3Executor
    )
  }

  @AfterEach
  fun tearDown() {
    if (s3Executor is java.util.concurrent.ExecutorService) {
      (s3Executor as java.util.concurrent.ExecutorService).shutdown()
      (s3Executor as java.util.concurrent.ExecutorService).awaitTermination(1, TimeUnit.SECONDS)
    }
  }

  @Test
  fun `query with no S3 pointers returns original response`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val items = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("USER#123").build(),
        "sk" to AttributeValue.builder().s("PROFILE").build(),
        "name" to AttributeValue.builder().s("John Doe").build()
      ),
      mapOf(
        "pk" to AttributeValue.builder().s("USER#124").build(),
        "sk" to AttributeValue.builder().s("PROFILE").build(),
        "name" to AttributeValue.builder().s("Jane Doe").build()
      )
    )

    val queryResponse = QueryResponse.builder()
      .items(items)
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then
    assertThat(result).isEqualTo(queryResponse)
    verify(s3Client, never()).getObject(any<String>(), any<String>())
  }

  @Test
  fun `query with S3 pointer hydrates from S3`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user123/2024-01-15.json").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // S3 object content
    val s3Data = """
      {
        "pk": {"S": "USER#123"},
        "sk": {"S": "TRANSACTION#2024-01-15"},
        "amount": {"N": "100.50"},
        "description": {"S": "Test transaction"},
        "metadata": {"S": "{\"category\":\"food\",\"merchant\":\"Test Store\"}"}
      }
    """.trimIndent()

    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json")))
      .thenReturn(s3Object)

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(1)
    val hydratedItem = result.items().first()
    assertThat(hydratedItem["pk"]?.s()).isEqualTo("USER#123")
    assertThat(hydratedItem["sk"]?.s()).isEqualTo("TRANSACTION#2024-01-15")
    assertThat(hydratedItem["amount"]?.n()).isEqualTo("100.50")
    assertThat(hydratedItem["description"]?.s()).isEqualTo("Test transaction")
    assertThat(hydratedItem["_s3_pointer"]?.s()).isEqualTo("s3://transactions/user123/2024-01-15.json")

    verify(s3Client).getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json"))
  }

  @Test
  fun `query with multiple S3 pointers hydrates in parallel`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItems = (1..5).map { i ->
      mapOf(
        "pk" to AttributeValue.builder().s("USER#$i").build(),
        "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-$i").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user$i/2024-01-$i.json").build()
      )
    }

    val queryResponse = QueryResponse.builder()
      .items(pointerItems)
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // S3 objects
    pointerItems.forEachIndexed { index, item ->
      val userId = index + 1
      val s3Data = """
        {
          "pk": {"S": "USER#$userId"},
          "sk": {"S": "TRANSACTION#2024-01-$userId"},
          "amount": {"N": "${userId * 10.5}"}
        }
      """.trimIndent()

      val s3Object = mock<S3Object>()
      val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
      whenever(s3Object.objectContent).thenReturn(s3InputStream)
      whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user$userId/2024-01-$userId.json")))
        .thenReturn(s3Object)
    }

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(5)
    result.items().forEachIndexed { index, item ->
      val userId = index + 1
      assertThat(item["pk"]?.s()).isEqualTo("USER#$userId")
      assertThat(item["amount"]?.n()).isEqualTo("${userId * 10.5}")
    }

    // Verify all S3 objects were fetched
    (1..5).forEach { i ->
      verify(s3Client).getObject(eq("test-bucket"), eq("transactions/user$i/2024-01-$i.json"))
    }
  }

  @Test
  fun `query with S3 pointer handles gzipped content`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user123/2024-01-15.json.gz").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // Gzipped S3 content
    val s3Data = """
      {
        "pk": {"S": "USER#123"},
        "sk": {"S": "TRANSACTION#2024-01-15"},
        "amount": {"N": "100.50"}
      }
    """.trimIndent()

    val compressedData = ByteArrayOutputStream().use { baos ->
      GZIPOutputStream(baos).use { gzip ->
        gzip.write(s3Data.toByteArray())
      }
      baos.toByteArray()
    }

    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(compressedData), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json.gz")))
      .thenReturn(s3Object)

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(1)
    val hydratedItem = result.items().first()
    assertThat(hydratedItem["amount"]?.n()).isEqualTo("100.50")
  }

  @Test
  fun `query returns pointer item when S3 read fails`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user123/2024-01-15.json").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)
    whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json")))
      .thenThrow(RuntimeException("S3 error"))

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(1)
    assertThat(result.items().first()).isEqualTo(pointerItem)
  }

  @Test
  fun `scan with S3 pointers hydrates correctly`() {
    // Given
    val scanRequest = ScanRequest.builder()
      .tableName("test-table")
      .build()

    val regularItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#100").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "name" to AttributeValue.builder().s("Regular User").build()
    )

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user123/2024-01-15.json").build()
    )

    val scanResponse = ScanResponse.builder()
      .items(listOf(regularItem, pointerItem))
      .build()

    whenever(delegateClient.scan(scanRequest)).thenReturn(scanResponse)

    // S3 object content
    val s3Data = """
      {
        "pk": {"S": "USER#123"},
        "sk": {"S": "TRANSACTION#2024-01-15"},
        "amount": {"N": "100.50"}
      }
    """.trimIndent()

    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json")))
      .thenReturn(s3Object)

    // When
    val result = s3AwareClient.scan(scanRequest)

    // Then
    assertThat(result.items()).hasSize(2)
    assertThat(result.items()[0]).isEqualTo(regularItem) // Regular item unchanged
    assertThat(result.items()[1]["amount"]?.n()).isEqualTo("100.50") // Pointer item hydrated
  }

  @Test
  fun `getItem with S3 pointer hydrates from S3`() {
    // Given
    val getItemRequest = GetItemRequest.builder()
      .tableName("test-table")
      .key(mapOf(
        "pk" to AttributeValue.builder().s("USER#123").build(),
        "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build()
      ))
      .build()

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user123/2024-01-15.json").build()
    )

    val getItemResponse = GetItemResponse.builder()
      .item(pointerItem)
      .build()

    whenever(delegateClient.getItem(getItemRequest)).thenReturn(getItemResponse)

    // S3 object content
    val s3Data = """
      {
        "pk": {"S": "USER#123"},
        "sk": {"S": "TRANSACTION#2024-01-15"},
        "amount": {"N": "100.50"},
        "description": {"S": "Test transaction"}
      }
    """.trimIndent()

    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json")))
      .thenReturn(s3Object)

    // When
    val result = s3AwareClient.getItem(getItemRequest)

    // Then
    assertThat(result.hasItem()).isTrue()
    val hydratedItem = result.item()
    assertThat(hydratedItem["amount"]?.n()).isEqualTo("100.50")
    assertThat(hydratedItem["description"]?.s()).isEqualTo("Test transaction")
  }

  @Test
  fun `putItem passes through unchanged`() {
    // Given
    val putItemRequest = PutItemRequest.builder()
      .tableName("test-table")
      .item(mapOf(
        "pk" to AttributeValue.builder().s("USER#123").build(),
        "sk" to AttributeValue.builder().s("PROFILE").build(),
        "name" to AttributeValue.builder().s("John Doe").build()
      ))
      .build()

    val putItemResponse = PutItemResponse.builder().build()
    whenever(delegateClient.putItem(putItemRequest)).thenReturn(putItemResponse)

    // When
    val result = s3AwareClient.putItem(putItemRequest)

    // Then
    assertThat(result).isEqualTo(putItemResponse)
    verify(delegateClient).putItem(putItemRequest)
    verify(s3Client, never()).getObject(any<String>(), any<String>())
  }

  @Test
  fun `handles malformed JSON from S3`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-15").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user123/2024-01-15.json").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // Malformed JSON
    val s3Data = """
      {
        "pk": {"S": "USER#123",
        "sk": {"S": "TRANSACTION#2024-01-15"}
        // missing closing brace
    """.trimIndent()

    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user123/2024-01-15.json")))
      .thenReturn(s3Object)

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then - should return the original pointer item on JSON parse failure
    assertThat(result.items()).hasSize(1)
    assertThat(result.items().first()).isEqualTo(pointerItem)
  }

  @Test
  fun `sequential mode when no executor provided`() {
    // Given - create client without executor
    val sequentialClient = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = hybridConfig,
      s3Executor = null  // No executor
    )

    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItems = (1..3).map { i ->
      mapOf(
        "pk" to AttributeValue.builder().s("USER#$i").build(),
        "sk" to AttributeValue.builder().s("TRANSACTION#2024-01-$i").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://transactions/user$i/2024-01-$i.json").build()
      )
    }

    val queryResponse = QueryResponse.builder()
      .items(pointerItems)
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // S3 objects
    pointerItems.forEachIndexed { index, item ->
      val userId = index + 1
      val s3Data = """
        {
          "pk": {"S": "USER#$userId"},
          "sk": {"S": "TRANSACTION#2024-01-$userId"},
          "amount": {"N": "${userId * 10}"}
        }
      """.trimIndent()

      val s3Object = mock<S3Object>()
      val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
      whenever(s3Object.objectContent).thenReturn(s3InputStream)
      whenever(s3Client.getObject(eq("test-bucket"), eq("transactions/user$userId/2024-01-$userId.json")))
        .thenReturn(s3Object)
    }

    // When
    val result = sequentialClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(3)
    result.items().forEachIndexed { index, item ->
      val userId = index + 1
      assertThat(item["amount"]?.n()).isEqualTo("${userId * 10}")
    }
  }

  @Test
  fun `handles complex DynamoDB types correctly`() {
    // Given
    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("COMPLEX").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://complex/data.json").build()
    )

    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // Complex S3 data with various DynamoDB types
    val s3Data = """
      {
        "pk": {"S": "USER#123"},
        "sk": {"S": "COMPLEX"},
        "stringSet": {"SS": ["value1", "value2", "value3"]},
        "numberSet": {"NS": ["1", "2", "3"]},
        "list": {"L": [
          {"S": "item1"},
          {"N": "42"},
          {"BOOL": true}
        ]},
        "map": {"M": {
          "nested": {"S": "value"},
          "count": {"N": "10"}
        }},
        "nullValue": {"NULL": true},
        "boolValue": {"BOOL": false}
      }
    """.trimIndent()

    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq("complex/data.json")))
      .thenReturn(s3Object)

    // When
    val result = s3AwareClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(1)
    val hydratedItem = result.items().first()

    // Verify complex types
    assertThat(hydratedItem["stringSet"]?.ss()).containsExactly("value1", "value2", "value3")
    assertThat(hydratedItem["numberSet"]?.ns()).containsExactly("1", "2", "3")
    assertThat(hydratedItem["list"]?.l()).hasSize(3)
    assertThat(hydratedItem["list"]?.l()?.get(0)?.s()).isEqualTo("item1")
    assertThat(hydratedItem["list"]?.l()?.get(1)?.n()).isEqualTo("42")
    assertThat(hydratedItem["list"]?.l()?.get(2)?.bool()).isTrue()
    assertThat(hydratedItem["map"]?.m()?.get("nested")?.s()).isEqualTo("value")
    assertThat(hydratedItem["map"]?.m()?.get("count")?.n()).isEqualTo("10")
    assertThat(hydratedItem["nullValue"]?.nul()).isTrue()
    assertThat(hydratedItem["boolValue"]?.bool()).isFalse()
  }
}

private fun ByteArrayOutputStream(): java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream()