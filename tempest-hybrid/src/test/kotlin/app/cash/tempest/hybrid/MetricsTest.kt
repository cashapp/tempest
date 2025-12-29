package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.ByteArrayInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.ScanResponse

class MetricsTest {

  private lateinit var delegateClient: DynamoDbClient
  private lateinit var s3Client: AmazonS3
  private lateinit var objectMapper: ObjectMapper
  private lateinit var metrics: CountingMetrics

  @BeforeEach
  fun setup() {
    delegateClient = mock()
    s3Client = mock()
    objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    metrics = CountingMetrics()
  }

  @Test
  fun `metrics are recorded for successful S3 hydration`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      metrics = metrics
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config
    )

    // Setup S3 pointer item
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://user-123/transaction-2024.json").build()
    )

    // Setup S3 response
    val fullData = """
      {
        "pk": "USER#123",
        "sk": "TRANSACTION#2024",
        "amount": 100.50,
        "description": "Test transaction"
      }
    """.trimIndent()

    val s3Object = mock<S3Object>()
    val inputStream = S3ObjectInputStream(ByteArrayInputStream(fullData.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(inputStream)
    whenever(s3Client.getObject("test-bucket", "user-123/transaction-2024.json")).thenReturn(s3Object)

    // Setup DynamoDB response
    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()
    whenever(delegateClient.query(any<QueryRequest>())).thenReturn(queryResponse)

    // When
    val request = QueryRequest.builder().tableName("test-table").build()
    val response = client.query(request)

    // Then
    assertThat(response.items()).hasSize(1)
    assertThat(response.items()[0]["amount"]?.n()).isEqualTo("100.5")

    // Verify metrics were recorded
    assertThat(metrics.hydrationCount()).isEqualTo(1)
    assertThat(metrics.successCount()).isEqualTo(1)
    assertThat(metrics.failureCount()).isEqualTo(0)

    val hydrationEvent = metrics.events.filterIsInstance<MetricEvent.Hydration>().first()
    assertThat(hydrationEvent.operation).isEqualTo("query")
    assertThat(hydrationEvent.success).isTrue()
    assertThat(hydrationEvent.latencyMs).isGreaterThanOrEqualTo(0)

    val batchEvent = metrics.events.filterIsInstance<MetricEvent.BatchComplete>().first()
    assertThat(batchEvent.operation).isEqualTo("query")
    assertThat(batchEvent.itemCount).isEqualTo(1)
    assertThat(batchEvent.successCount).isEqualTo(1)
  }

  @Test
  fun `metrics are recorded for failed S3 hydration`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      errorStrategy = HybridConfig.ErrorStrategy.SKIP_FAILED,
      metrics = metrics
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config
    )

    // Setup S3 pointer item
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://user-123/transaction-2024.json").build()
    )

    // Setup S3 failure
    whenever(s3Client.getObject(any<String>(), any<String>()))
      .thenThrow(RuntimeException("S3 read failed"))

    // Setup DynamoDB response
    val scanResponse = ScanResponse.builder()
      .items(listOf(pointerItem))
      .build()
    whenever(delegateClient.scan(any<ScanRequest>())).thenReturn(scanResponse)

    // When
    val request = ScanRequest.builder().tableName("test-table").build()
    val response = client.scan(request)

    // Then - item should be skipped due to SKIP_FAILED strategy
    assertThat(response.items()).isEmpty()

    // Verify metrics were recorded
    assertThat(metrics.hydrationCount()).isEqualTo(1)
    assertThat(metrics.successCount()).isEqualTo(0)
    assertThat(metrics.failureCount()).isEqualTo(1)

    val hydrationEvent = metrics.events.filterIsInstance<MetricEvent.Hydration>().first()
    assertThat(hydrationEvent.operation).isEqualTo("scan")
    assertThat(hydrationEvent.success).isFalse()
    assertThat(hydrationEvent.latencyMs).isGreaterThanOrEqualTo(0)

    val batchEvent = metrics.events.filterIsInstance<MetricEvent.BatchComplete>().first()
    assertThat(batchEvent.operation).isEqualTo("scan")
    assertThat(batchEvent.itemCount).isEqualTo(1)
    assertThat(batchEvent.successCount).isEqualTo(0)
  }

  @Test
  fun `no metrics are recorded when metrics is null`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      metrics = null // No metrics configured
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config
    )

    // Setup regular item (no S3 pointer)
    val regularItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024").build(),
      "amount" to AttributeValue.builder().n("100.50").build()
    )

    // Setup DynamoDB response
    val queryResponse = QueryResponse.builder()
      .items(listOf(regularItem))
      .build()
    whenever(delegateClient.query(any<QueryRequest>())).thenReturn(queryResponse)

    // When
    val request = QueryRequest.builder().tableName("test-table").build()
    val response = client.query(request)

    // Then
    assertThat(response.items()).hasSize(1)
    assertThat(response.items()[0]["amount"]?.n()).isEqualTo("100.50")

    // No metrics should have been recorded
    assertThat(metrics.events).isEmpty()
  }

  @Test
  fun `batch metrics are recorded for multiple items`() {
    // Given
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      metrics = metrics
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config
    )

    // Setup multiple S3 pointer items
    val pointerItems = (1..3).map { i ->
      mapOf(
        "pk" to AttributeValue.builder().s("USER#$i").build(),
        "sk" to AttributeValue.builder().s("TRANSACTION#2024").build(),
        "_s3_pointer" to AttributeValue.builder().s("s3://user-$i/transaction.json").build()
      )
    }

    // Setup S3 responses
    pointerItems.forEachIndexed { i, _ ->
      val fullData = """
        {
          "pk": "USER#${i + 1}",
          "sk": "TRANSACTION#2024",
          "amount": ${100.50 + i}
        }
      """.trimIndent()

      val s3Object = mock<S3Object>()
      val inputStream = S3ObjectInputStream(ByteArrayInputStream(fullData.toByteArray()), null)
      whenever(s3Object.objectContent).thenReturn(inputStream)
      whenever(s3Client.getObject("test-bucket", "user-${i + 1}/transaction.json")).thenReturn(s3Object)
    }

    // Setup DynamoDB response
    val queryResponse = QueryResponse.builder()
      .items(pointerItems)
      .build()
    whenever(delegateClient.query(any<QueryRequest>())).thenReturn(queryResponse)

    // When
    val request = QueryRequest.builder().tableName("test-table").build()
    val response = client.query(request)

    // Then
    assertThat(response.items()).hasSize(3)

    // Verify metrics were recorded
    assertThat(metrics.hydrationCount()).isEqualTo(3) // 3 individual hydrations
    assertThat(metrics.successCount()).isEqualTo(3)
    assertThat(metrics.failureCount()).isEqualTo(0)

    val batchEvent = metrics.events.filterIsInstance<MetricEvent.BatchComplete>().first()
    assertThat(batchEvent.operation).isEqualTo("query")
    assertThat(batchEvent.itemCount).isEqualTo(3)
    assertThat(batchEvent.successCount).isEqualTo(3)
  }

  @Test
  fun `conditional metrics only record when condition is met`() {
    // Given
    var shouldRecord = false
    val conditionalMetrics = ConditionalMetrics(metrics) { shouldRecord }

    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(bucketName = "test-bucket"),
      metrics = conditionalMetrics
    )

    val client = S3AwareDynamoDbClient(
      delegate = delegateClient,
      s3Client = s3Client,
      objectMapper = objectMapper,
      hybridConfig = config
    )

    // Setup S3 pointer item
    val pointerItem = mapOf(
      "pk" to AttributeValue.builder().s("USER#123").build(),
      "sk" to AttributeValue.builder().s("TRANSACTION#2024").build(),
      "_s3_pointer" to AttributeValue.builder().s("s3://user-123/transaction.json").build()
    )

    // Setup S3 response
    val fullData = """{"pk": "USER#123", "sk": "TRANSACTION#2024"}""".trimIndent()
    val s3Object = mock<S3Object>()
    val inputStream = S3ObjectInputStream(ByteArrayInputStream(fullData.toByteArray()), null)
    whenever(s3Object.objectContent).thenReturn(inputStream)
    whenever(s3Client.getObject(any<String>(), any<String>())).thenReturn(s3Object)

    // Setup DynamoDB response
    val queryResponse = QueryResponse.builder()
      .items(listOf(pointerItem))
      .build()
    whenever(delegateClient.query(any<QueryRequest>())).thenReturn(queryResponse)

    // When - first query with condition false
    shouldRecord = false
    client.query(QueryRequest.builder().tableName("test-table").build())

    // Then
    assertThat(metrics.events).isEmpty()

    // When - second query with condition true
    shouldRecord = true
    client.query(QueryRequest.builder().tableName("test-table").build())

    // Then
    assertThat(metrics.events).hasSize(2) // 1 hydration + 1 batch complete
  }
}