# Tempest-Hybrid Usage Examples

Complete, production-ready examples for common use cases.

## Table of Contents

1. [Basic Setup](#basic-setup)
2. [Spring Boot Application](#spring-boot-application)
3. [Archival Job Implementation](#archival-job-implementation)
4. [Custom Error Handling](#custom-error-handling)
5. [Performance Optimization](#performance-optimization)
6. [Testing Examples](#testing-examples)
7. [Monitoring and Metrics](#monitoring-and-metrics)

## Basic Setup

### Minimal Configuration

```kotlin
import app.cash.tempest.hybrid.*
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.Executors

fun createHybridClient(): DynamoDbClient {
    // Create S3 client
    val s3Client = AmazonS3ClientBuilder.standard()
        .withRegion("us-east-1")
        .build()

    // Create executor for parallel reads
    val executor = Executors.newFixedThreadPool(10)

    // Configure hybrid storage
    val config = HybridConfig(
        s3Config = HybridConfig.S3Config(
            bucketName = "my-dynamodb-archive"
        )
    )

    // Create factory
    val factory = HybridDynamoDbFactory(
        s3Client = s3Client,
        objectMapper = ObjectMapper(),
        hybridConfig = config,
        s3Executor = executor
    )

    // Wrap existing client
    return factory.wrapDynamoDbClient(originalDynamoDbClient)
}
```

## Spring Boot Application

### Complete Configuration

```kotlin
@Configuration
@EnableScheduling
class DynamoDbHybridConfig {

    @Bean
    fun amazonS3(
        @Value("\${aws.region}") region: String,
        @Value("\${aws.s3.endpoint:}") endpoint: String?
    ): AmazonS3 {
        val builder = AmazonS3ClientBuilder.standard()
            .withRegion(region)
            .withCredentials(DefaultAWSCredentialsProviderChain())

        endpoint?.let {
            builder.withEndpointConfiguration(
                EndpointConfiguration(it, region)
            )
        }

        return builder.build()
    }

    @Bean
    fun s3ExecutorService(
        @Value("\${hybrid.s3.threads:10}") threadCount: Int
    ): ExecutorService {
        return ThreadPoolExecutor(
            threadCount / 2,  // Core pool size
            threadCount,       // Maximum pool size
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(100),
            ThreadFactoryBuilder()
                .setNameFormat("s3-hydration-%d")
                .setDaemon(true)
                .build(),
            ThreadPoolExecutor.CallerRunsPolicy()  // Fallback to caller thread
        )
    }

    @Bean
    fun hybridConfig(
        @Value("\${aws.s3.bucket}") bucketName: String,
        @Value("\${aws.s3.prefix:dynamodb/}") prefix: String
    ): HybridConfig {
        return HybridConfig(
            s3Config = HybridConfig.S3Config(
                bucketName = bucketName,
                keyPrefix = prefix,
                region = null
            )
        )
    }

    @Bean
    @Primary
    fun hybridDynamoDbClient(
        dynamoDbClient: DynamoDbClient,
        amazonS3: AmazonS3,
        s3ExecutorService: ExecutorService,
        hybridConfig: HybridConfig
    ): DynamoDbClient {
        val factory = HybridDynamoDbFactory(
            s3Client = amazonS3,
            objectMapper = jacksonObjectMapper(),
            hybridConfig = hybridConfig,
            s3Executor = s3ExecutorService
        )

        return factory.wrapDynamoDbClient(dynamoDbClient)
    }

    @PreDestroy
    fun cleanup() {
        s3ExecutorService.shutdown()
        try {
            if (!s3ExecutorService.awaitTermination(30, TimeUnit.SECONDS)) {
                s3ExecutorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            s3ExecutorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
```

### Repository Using Hybrid Storage

```kotlin
@Repository
class TransactionRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val tableName: String = "transactions"
) {

    fun findByUserId(userId: String): List<Transaction> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk")
            .expressionAttributeValues(mapOf(
                ":pk" to AttributeValue.builder().s("USER#$userId").build()
            ))
            .build()

        // Hybrid client handles S3 hydration transparently
        val response = dynamoDbClient.query(request)

        return response.items().map { item ->
            deserializeTransaction(item)
        }
    }

    fun findByDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Transaction> {
        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("pk = :pk AND sk BETWEEN :start AND :end")
            .expressionAttributeValues(mapOf(
                ":pk" to AttributeValue.builder().s("USER#$userId").build(),
                ":start" to AttributeValue.builder().s("TX#$startDate").build(),
                ":end" to AttributeValue.builder().s("TX#$endDate").build()
            ))
            .build()

        val response = dynamoDbClient.query(request)
        return response.items().map { deserializeTransaction(it) }
    }
}
```

## Archival Job Implementation

### Scheduled Archival Service

```kotlin
@Service
class S3ArchivalService(
    private val dynamoDbClient: DynamoDbClient,
    private val s3Client: AmazonS3,
    private val objectMapper: ObjectMapper,
    @Value("\${archival.bucket}") private val bucketName: String,
    @Value("\${archival.days-to-archive:30}") private val daysToArchive: Int
) {

    companion object {
        private val logger = LoggerFactory.getLogger(S3ArchivalService::class.java)
    }

    @Scheduled(cron = "\${archival.cron:0 0 2 * * *}")
    fun archiveOldItems() {
        logger.info("Starting archival job")
        val startTime = System.currentTimeMillis()
        var archivedCount = 0
        var errorCount = 0

        try {
            val cutoffDate = LocalDate.now().minusDays(daysToArchive.toLong())
            val itemsToArchive = findItemsToArchive(cutoffDate)

            logger.info("Found ${itemsToArchive.size} items to archive")

            for (batch in itemsToArchive.chunked(25)) {
                val results = archiveBatch(batch)
                archivedCount += results.count { it.success }
                errorCount += results.count { !it.success }
            }

        } catch (e: Exception) {
            logger.error("Archival job failed", e)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logger.info(
                "Archival job completed: archived=$archivedCount, " +
                "errors=$errorCount, duration=${duration}ms"
            )
        }
    }

    private fun archiveBatch(
        items: List<Map<String, AttributeValue>>
    ): List<ArchivalResult> {
        return items.map { item ->
            try {
                archiveItem(item)
                ArchivalResult(success = true)
            } catch (e: Exception) {
                logger.error("Failed to archive item: ${item["pk"]?.s()}", e)
                ArchivalResult(success = false, error = e.message)
            }
        }
    }

    private fun archiveItem(item: Map<String, AttributeValue>) {
        val pk = item["pk"]?.s() ?: throw IllegalStateException("Missing pk")
        val sk = item["sk"]?.s() ?: throw IllegalStateException("Missing sk")

        // 1. Generate S3 key
        val s3Key = "dynamodb/archive/${pk}/${sk}.json.gz"

        // 2. Convert to JSON
        val json = convertToJson(item)

        // 3. Compress
        val compressed = gzipCompress(json)

        // 4. Upload to S3
        val putRequest = PutObjectRequest()
            .withBucketName(bucketName)
            .withKey(s3Key)
            .withMetadata(ObjectMetadata().apply {
                contentEncoding = "gzip"
                contentType = "application/json"
                addUserMetadata("original-size", json.length.toString())
                addUserMetadata("archived-date", Instant.now().toString())
            })

        s3Client.putObject(putRequest, ByteArrayInputStream(compressed))

        // 5. Create pointer in DynamoDB
        val pointer = mapOf(
            "pk" to item["pk"]!!,
            "sk" to item["sk"]!!,
            "_s3_pointer" to AttributeValue.builder()
                .s("s3://$s3Key")
                .build(),
            "archived_at" to AttributeValue.builder()
                .s(Instant.now().toString())
                .build()
        )

        val putItemRequest = PutItemRequest.builder()
            .tableName("transactions")
            .item(pointer)
            .build()

        dynamoDbClient.putItem(putItemRequest)

        logger.debug("Archived item to S3: $s3Key")
    }

    private fun gzipCompress(data: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(data.toByteArray(StandardCharsets.UTF_8))
        }
        return baos.toByteArray()
    }

    data class ArchivalResult(
        val success: Boolean,
        val error: String? = null
    )
}
```

## Custom Error Handling

### Resilient Hybrid Client

```kotlin
@Component
class ResilientHybridClient(
    private val hybridClient: DynamoDbClient,
    private val meterRegistry: MeterRegistry
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ResilientHybridClient::class.java)
    }

    fun queryWithRetry(
        request: QueryRequest,
        maxRetries: Int = 3
    ): QueryResponse {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = hybridClient.query(request)
                recordSuccess()
                return response
            } catch (e: Exception) {
                lastException = e
                recordFailure(e)

                if (attempt < maxRetries - 1) {
                    val delay = calculateBackoff(attempt)
                    logger.warn(
                        "Query failed (attempt ${attempt + 1}/$maxRetries), " +
                        "retrying in ${delay}ms", e
                    )
                    Thread.sleep(delay)
                }
            }
        }

        throw RetryExhaustedException(
            "Query failed after $maxRetries attempts",
            lastException
        )
    }

    private fun calculateBackoff(attempt: Int): Long {
        return (100 * Math.pow(2.0, attempt.toDouble())).toLong()
            .coerceAtMost(5000)  // Max 5 seconds
    }

    private fun recordSuccess() {
        meterRegistry.counter("dynamodb.query", "status", "success").increment()
    }

    private fun recordFailure(e: Exception) {
        meterRegistry.counter(
            "dynamodb.query",
            "status", "failure",
            "error", e.javaClass.simpleName
        ).increment()
    }
}
```

## Performance Optimization

### Caching Layer

```kotlin
@Component
class CachedHybridRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val cacheManager: CacheManager
) {

    fun getTransactionWithCache(
        userId: String,
        transactionId: String
    ): Transaction? {
        val cacheKey = "$userId:$transactionId"

        // Check cache first
        return cacheManager.getCache("transactions")
            ?.get(cacheKey, Transaction::class.java)
            ?: run {
                // Not in cache, query DynamoDB (with S3 hydration)
                val transaction = queryTransaction(userId, transactionId)

                // Cache the result
                transaction?.let {
                    cacheManager.getCache("transactions")?.put(cacheKey, it)
                }

                transaction
            }
    }

    private fun queryTransaction(
        userId: String,
        transactionId: String
    ): Transaction? {
        val request = GetItemRequest.builder()
            .tableName("transactions")
            .key(mapOf(
                "pk" to AttributeValue.builder().s("USER#$userId").build(),
                "sk" to AttributeValue.builder().s("TX#$transactionId").build()
            ))
            .build()

        val response = dynamoDbClient.getItem(request)
        return if (response.hasItem()) {
            deserializeTransaction(response.item())
        } else null
    }
}
```

### Batch Processing

```kotlin
@Component
class BatchProcessor(
    private val hybridClient: DynamoDbClient,
    private val executor: ExecutorService
) {

    fun processBatchWithHydration(
        userIds: List<String>
    ): Map<String, List<Transaction>> {
        val futures = userIds.map { userId ->
            CompletableFuture.supplyAsync({
                userId to fetchUserTransactions(userId)
            }, executor)
        }

        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply {
                futures.map { it.get() }.toMap()
            }
            .get(30, TimeUnit.SECONDS)
    }

    private fun fetchUserTransactions(userId: String): List<Transaction> {
        val request = QueryRequest.builder()
            .tableName("transactions")
            .keyConditionExpression("pk = :pk")
            .expressionAttributeValues(mapOf(
                ":pk" to AttributeValue.builder().s("USER#$userId").build()
            ))
            .build()

        // Hybrid client handles S3 hydration
        val response = hybridClient.query(request)
        return response.items().map { deserializeTransaction(it) }
    }
}
```

## Testing Examples

### Integration Test

```kotlin
@SpringBootTest
@TestPropertySource(properties = [
    "aws.s3.bucket=test-bucket",
    "hybrid.s3.threads=5"
])
class HybridStorageIntegrationTest {

    @Autowired
    private lateinit var hybridClient: DynamoDbClient

    @MockBean
    private lateinit var s3Client: AmazonS3

    @Test
    fun `should hydrate S3 pointer transparently`() {
        // Given: S3 contains full data
        val s3Data = """
            {
                "pk": {"S": "USER#123"},
                "sk": {"S": "TX#456"},
                "amount": {"N": "100.50"},
                "description": {"S": "Test transaction"}
            }
        """.trimIndent()

        val s3Object = mock<S3Object>()
        whenever(s3Object.objectContent).thenReturn(
            S3ObjectInputStream(
                ByteArrayInputStream(s3Data.toByteArray()),
                null
            )
        )
        whenever(s3Client.getObject("test-bucket", "test-key"))
            .thenReturn(s3Object)

        // When: Query returns pointer
        val request = GetItemRequest.builder()
            .tableName("transactions")
            .key(mapOf(
                "pk" to AttributeValue.builder().s("USER#123").build(),
                "sk" to AttributeValue.builder().s("TX#456").build()
            ))
            .build()

        val response = hybridClient.getItem(request)

        // Then: Full data is returned
        assertThat(response.item()["amount"]?.n()).isEqualTo("100.50")
        assertThat(response.item()["description"]?.s())
            .isEqualTo("Test transaction")
    }
}
```

### Performance Test

```kotlin
@Test
fun `parallel hydration should be faster than sequential`() {
    // Setup
    val itemCount = 50
    val mockS3Latency = 50L  // milliseconds

    // Configure S3 mock with artificial latency
    whenever(s3Client.getObject(any(), any())).thenAnswer {
        Thread.sleep(mockS3Latency)
        createMockS3Object()
    }

    // Test sequential
    val sequentialClient = createHybridClient(executor = null)
    val sequentialTime = measureTimeMillis {
        sequentialClient.query(createQueryWith(itemCount))
    }

    // Test parallel
    val parallelClient = createHybridClient(
        executor = Executors.newFixedThreadPool(10)
    )
    val parallelTime = measureTimeMillis {
        parallelClient.query(createQueryWith(itemCount))
    }

    // Assert parallel is significantly faster
    assertThat(parallelTime).isLessThan(sequentialTime / 5)

    logger.info("Sequential: ${sequentialTime}ms, Parallel: ${parallelTime}ms")
}
```

## Monitoring and Metrics

### Comprehensive Metrics Collection

```kotlin
@Component
class HybridStorageMetrics(
    private val meterRegistry: MeterRegistry
) {

    fun recordHydration(
        tableName: String,
        itemCount: Int,
        duration: Long,
        success: Boolean,
        error: String? = null
    ) {
        // Success/failure counter
        meterRegistry.counter(
            "hybrid.s3.hydration",
            "table", tableName,
            "status", if (success) "success" else "failure",
            "error", error ?: "none"
        ).increment(itemCount.toDouble())

        // Latency histogram
        if (success) {
            meterRegistry.timer(
                "hybrid.s3.hydration.latency",
                "table", tableName
            ).record(duration, TimeUnit.MILLISECONDS)
        }

        // Items per query
        meterRegistry.summary(
            "hybrid.s3.hydration.batch_size",
            "table", tableName
        ).record(itemCount.toDouble())
    }

    fun recordArchival(
        tableName: String,
        itemSize: Long,
        compressionRatio: Double,
        success: Boolean
    ) {
        if (success) {
            // Track sizes
            meterRegistry.gauge(
                "hybrid.archival.item_size",
                Tags.of("table", tableName),
                itemSize
            )

            // Track compression
            meterRegistry.gauge(
                "hybrid.archival.compression_ratio",
                Tags.of("table", tableName),
                compressionRatio
            )
        }

        // Success/failure
        meterRegistry.counter(
            "hybrid.archival.operations",
            "table", tableName,
            "status", if (success) "success" else "failure"
        ).increment()
    }

    fun getStats(): HybridStats {
        val hydrationSuccess = meterRegistry.counter(
            "hybrid.s3.hydration",
            "status", "success"
        ).count()

        val hydrationFailure = meterRegistry.counter(
            "hybrid.s3.hydration",
            "status", "failure"
        ).count()

        val avgLatency = meterRegistry.timer(
            "hybrid.s3.hydration.latency"
        ).mean(TimeUnit.MILLISECONDS)

        return HybridStats(
            hydrationSuccessCount = hydrationSuccess.toLong(),
            hydrationFailureCount = hydrationFailure.toLong(),
            hydrationSuccessRate = if (hydrationSuccess + hydrationFailure > 0) {
                hydrationSuccess / (hydrationSuccess + hydrationFailure)
            } else 0.0,
            averageHydrationLatencyMs = avgLatency
        )
    }

    data class HybridStats(
        val hydrationSuccessCount: Long,
        val hydrationFailureCount: Long,
        val hydrationSuccessRate: Double,
        val averageHydrationLatencyMs: Double
    )
}
```

### Health Check

```kotlin
@Component
class HybridStorageHealthIndicator(
    private val s3Client: AmazonS3,
    private val hybridConfig: HybridConfig,
    private val metrics: HybridStorageMetrics
) : HealthIndicator {

    override fun health(): Health {
        val stats = metrics.getStats()

        // Check S3 connectivity
        val s3Healthy = try {
            s3Client.doesBucketExistV2(hybridConfig.s3Config.bucketName)
        } catch (e: Exception) {
            false
        }

        // Check hydration success rate
        val hydrationHealthy = stats.hydrationSuccessRate > 0.95

        return if (s3Healthy && hydrationHealthy) {
            Health.up()
                .withDetail("s3_bucket", hybridConfig.s3Config.bucketName)
                .withDetail("hydration_success_rate", stats.hydrationSuccessRate)
                .withDetail("avg_latency_ms", stats.averageHydrationLatencyMs)
                .build()
        } else {
            Health.down()
                .withDetail("s3_healthy", s3Healthy)
                .withDetail("hydration_healthy", hydrationHealthy)
                .withDetail("hydration_success_rate", stats.hydrationSuccessRate)
                .build()
        }
    }
}
```