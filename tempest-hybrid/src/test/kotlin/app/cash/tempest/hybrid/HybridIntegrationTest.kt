package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class HybridIntegrationTest {

  private lateinit var delegateClient: DynamoDbClient
  private lateinit var s3Client: AmazonS3
  private lateinit var objectMapper: ObjectMapper
  private lateinit var hybridConfig: HybridConfig
  private lateinit var executorService: java.util.concurrent.ExecutorService
  private lateinit var factory: HybridDynamoDbFactory

  @BeforeEach
  fun setUp() {
    delegateClient = mock()
    s3Client = mock()
    objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    hybridConfig = HybridConfig(
      s3Config = HybridConfig.S3Config(
        bucketName = "test-bucket",
        keyPrefix = "prefix/",
        region = "us-west-2"
      ),
      errorStrategy = HybridConfig.ErrorStrategy.RETURN_POINTER // Keep old behavior for existing tests
    )
    executorService = Executors.newFixedThreadPool(5)

    factory = HybridDynamoDbFactory(
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = hybridConfig,
      s3Executor = executorService
    )
  }

  @AfterEach
  fun tearDown() {
    executorService.shutdown()
    executorService.awaitTermination(5, TimeUnit.SECONDS)
  }

  @Test
  fun `integration test with mixed items - regular and S3 pointers`() {
    // Given
    val wrappedClient = factory.wrapDynamoDbClient(delegateClient)

    val regularItem1 = mapOf(
      "pk" to AttributeValue.builder().s("USER#100").build(),
      "sk" to AttributeValue.builder().s("PROFILE").build(),
      "name" to AttributeValue.builder().s("Alice").build(),
      "age" to AttributeValue.builder().n("30").build()
    )

    val pointerItem1 = mapOf(
      "pk" to AttributeValue.builder().s("USER#101").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#001").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://data/user101/tx001.json").build()
    )

    val regularItem2 = mapOf(
      "pk" to AttributeValue.builder().s("USER#102").build(),
      "sk" to AttributeValue.builder().s("SETTINGS").build(),
      "theme" to AttributeValue.builder().s("dark").build()
    )

    val pointerItem2 = mapOf(
      "pk" to AttributeValue.builder().s("USER#103").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#002").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://data/user103/tx002.json").build()
    )

    val queryRequest = QueryRequest.builder()
      .tableName("users")
      .build()

    val queryResponse = QueryResponse.builder()
      .items(listOf(regularItem1, pointerItem1, regularItem2, pointerItem2))
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // S3 data for pointer items
    val s3Data1 = """
      {
        "pk": {"S": "USER#101"},
        "sk": {"S": "TRANSACTION#001"},
        "amount": {"N": "250.75"},
        "currency": {"S": "USD"},
        "items": {"L": [
          {"S": "Book"},
          {"S": "Pen"}
        ]},
        "metadata": {"M": {
          "category": {"S": "education"},
          "priority": {"N": "1"}
        }}
      }
    """.trimIndent()

    val s3Data2 = """
      {
        "pk": {"S": "USER#103"},
        "sk": {"S": "TRANSACTION#002"},
        "amount": {"N": "1500.00"},
        "currency": {"S": "EUR"},
        "approved": {"BOOL": true}
      }
    """.trimIndent()

    setupS3Mock("data/user101/tx001.json", s3Data1)
    setupS3Mock("data/user103/tx002.json", s3Data2)

    // When
    val result = wrappedClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(4)

    // Regular item 1 - unchanged
    assertThat(result.items()[0]["name"]?.s()).isEqualTo("Alice")
    assertThat(result.items()[0]["age"]?.n()).isEqualTo("30")

    // Pointer item 1 - hydrated
    assertThat(result.items()[1]["amount"]?.n()).isEqualTo("250.75")
    assertThat(result.items()[1]["currency"]?.s()).isEqualTo("USD")
    assertThat(result.items()[1]["items"]?.l()).hasSize(2)
    assertThat(result.items()[1]["metadata"]?.m()?.get("category")?.s()).isEqualTo("education")

    // Regular item 2 - unchanged
    assertThat(result.items()[2]["theme"]?.s()).isEqualTo("dark")

    // Pointer item 2 - hydrated
    assertThat(result.items()[3]["amount"]?.n()).isEqualTo("1500.00")
    assertThat(result.items()[3]["currency"]?.s()).isEqualTo("EUR")
    assertThat(result.items()[3]["approved"]?.bool()).isTrue()

    // Verify S3 calls
    verify(s3Client, times(2)).getObject(any<String>(), any<String>())
  }

  @Test
  fun `performance test - parallel vs sequential hydration`() {
    // Given
    val itemCount = 20
    val pointerItems = (1..itemCount).map { i ->
      mapOf(
        "pk" to AttributeValue.builder().s("USER#$i").build(),
        "sk" to AttributeValue.builder().s("DATA#$i").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://data/user$i/data.json").build()
      )
    }

    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val queryResponse = QueryResponse.builder()
      .items(pointerItems)
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // Add artificial delay to S3 reads to simulate network latency
    val s3CallCount = AtomicInteger(0)
    val latch = CountDownLatch(itemCount)

    pointerItems.forEachIndexed { index, _ ->
      val userId = index + 1
      val s3Data = """
        {
          "pk": {"S": "USER#$userId"},
          "sk": {"S": "DATA#$userId"},
          "value": {"N": "$userId"}
        }
      """.trimIndent()

      whenever(s3Client.getObject(eq("test-bucket"), eq("data/user$userId/data.json")))
        .thenAnswer {
          Thread.sleep(50) // Simulate 50ms S3 latency
          s3CallCount.incrementAndGet()
          latch.countDown()

          val s3Object = mock<S3Object>()
          val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(s3Data.toByteArray()), null)
          whenever(s3Object.objectContent).thenReturn(s3InputStream)
          s3Object
        }
    }

    // Test with parallel executor
    val parallelClient = factory.wrapDynamoDbClient(delegateClient)

    val startTime = System.currentTimeMillis()
    val result = parallelClient.query(queryRequest)
    val parallelTime = System.currentTimeMillis() - startTime

    // Verify results
    assertThat(result.items()).hasSize(itemCount)
    assertThat(s3CallCount.get()).isEqualTo(itemCount)

    // With 5 threads and 20 items with 50ms each:
    // Sequential would take ~1000ms (20 * 50ms)
    // Parallel should take ~200-250ms (20/5 * 50ms + overhead)
    assertThat(parallelTime).isLessThan(500) // Should be much faster than sequential

    // Verify all S3 calls completed
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun `resilience test - partial S3 failures`() {
    // Given
    val wrappedClient = factory.wrapDynamoDbClient(delegateClient)

    val pointerItems = listOf(
      mapOf(
        "pk" to AttributeValue.builder().s("USER#1").build(),
        "sk" to AttributeValue.builder().s("DATA#1").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://data/user1/data.json").build()
      ),
      mapOf(
        "pk" to AttributeValue.builder().s("USER#2").build(),
        "sk" to AttributeValue.builder().s("DATA#2").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://data/user2/data.json").build()
      ),
      mapOf(
        "pk" to AttributeValue.builder().s("USER#3").build(),
        "sk" to AttributeValue.builder().s("DATA#3").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://data/user3/data.json").build()
      )
    )

    val queryRequest = QueryRequest.builder()
      .tableName("test-table")
      .build()

    val queryResponse = QueryResponse.builder()
      .items(pointerItems)
      .build()

    whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

    // Setup: First item succeeds, second fails, third succeeds
    setupS3Mock("data/user1/data.json", """
      {
        "pk": {"S": "USER#1"},
        "sk": {"S": "DATA#1"},
        "status": {"S": "success"}
      }
    """.trimIndent())

    whenever(s3Client.getObject(eq("test-bucket"), eq("data/user2/data.json")))
      .thenThrow(RuntimeException("S3 Service Unavailable"))

    setupS3Mock("data/user3/data.json", """
      {
        "pk": {"S": "USER#3"},
        "sk": {"S": "DATA#3"},
        "status": {"S": "success"}
      }
    """.trimIndent())

    // When
    val result = wrappedClient.query(queryRequest)

    // Then
    assertThat(result.items()).hasSize(3)

    // First item - hydrated successfully
    assertThat(result.items()[0]["status"]?.s()).isEqualTo("success")

    // Second item - returned as original pointer due to failure
    assertThat(result.items()[1]).isEqualTo(pointerItems[1])
    assertThat(result.items()[1]["_s3_pointer"]?.s()).isEqualTo("s3://data/user2/data.json")

    // Third item - hydrated successfully
    assertThat(result.items()[2]["status"]?.s()).isEqualTo("success")
  }

  @Test
  fun `concurrent queries work correctly`() {
    // Given
    val wrappedClient = factory.wrapDynamoDbClient(delegateClient)
    val queryCount = 10
    val latch = CountDownLatch(queryCount)
    val results = mutableListOf<QueryResponse>()

    // Setup mock responses for different queries
    (1..queryCount).forEach { queryId ->
      val queryRequest = QueryRequest.builder()
        .tableName("table-$queryId")
        .build()

      val pointerItem = mapOf(
        "pk" to AttributeValue.builder().s("QUERY#$queryId").build(),
        "sk" to AttributeValue.builder().s("DATA").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://data/query$queryId/data.json").build()
      )

      val queryResponse = QueryResponse.builder()
        .items(listOf(pointerItem))
        .build()

      whenever(delegateClient.query(queryRequest)).thenReturn(queryResponse)

      setupS3Mock("data/query$queryId/data.json", """
        {
          "pk": {"S": "QUERY#$queryId"},
          "sk": {"S": "DATA"},
          "queryId": {"N": "$queryId"}
        }
      """.trimIndent())
    }

    // When - execute queries concurrently
    val queryExecutor = Executors.newFixedThreadPool(5)
    (1..queryCount).forEach { queryId ->
      queryExecutor.submit {
        try {
          val queryRequest = QueryRequest.builder()
            .tableName("table-$queryId")
            .build()
          val response = wrappedClient.query(queryRequest)
          synchronized(results) {
            results.add(response)
          }
        } finally {
          latch.countDown()
        }
      }
    }

    // Then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    assertThat(results).hasSize(queryCount)

    // Verify each query got the correct hydrated data
    results.forEach { response ->
      assertThat(response.items()).hasSize(1)
      val item = response.items().first()
      val queryId = item["queryId"]?.n()?.toInt()
      assertThat(item["pk"]?.s()).isEqualTo("QUERY#$queryId")
    }

    queryExecutor.shutdown()
  }

  private fun setupS3Mock(key: String, content: String) {
    val s3Object = mock<S3Object>()
    val s3InputStream = S3ObjectInputStream(ByteArrayInputStream(content.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(s3InputStream)
    whenever(s3Client.getObject(eq("test-bucket"), eq(key))).thenReturn(s3Object)
  }
}