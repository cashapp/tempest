# Migration Guide: Adopting Tempest-Hybrid

This guide helps you migrate from standard DynamoDB storage to the hybrid DynamoDB + S3 approach.

## Table of Contents

1. [Pre-Migration Checklist](#pre-migration-checklist)
2. [Migration Strategies](#migration-strategies)
3. [Step-by-Step Migration](#step-by-step-migration)
4. [Archival Process Implementation](#archival-process-implementation)
5. [Testing Strategy](#testing-strategy)
6. [Rollback Plan](#rollback-plan)
7. [Post-Migration Monitoring](#post-migration-monitoring)

## Pre-Migration Checklist

Before starting migration, ensure:

- [ ] S3 bucket is created and configured
- [ ] IAM roles have necessary S3 permissions
- [ ] Identified tables/items suitable for S3 storage
- [ ] Analyzed data access patterns
- [ ] Estimated cost savings
- [ ] Prepared monitoring and alerting
- [ ] Tested in non-production environment

## Migration Strategies

### Strategy 1: Gradual Migration (Recommended)

Best for production systems with zero downtime requirements.

```kotlin
// Phase 1: Deploy hybrid library (read support only)
val hybridClient = hybridFactory.wrapDynamoDbClient(dynamoDbClient)

// Phase 2: Start archiving old data to S3 (external process)
// Phase 3: Monitor and validate
// Phase 4: Expand archival criteria
```

### Strategy 2: Big Bang Migration

For smaller datasets or during maintenance windows.

```kotlin
// 1. Deploy hybrid library
// 2. Run batch migration job
// 3. Switch all reads to hybrid client
```

### Strategy 3: New Data Only

Start with hybrid storage for new data only.

```kotlin
// 1. Deploy hybrid library
// 2. Archive only new items meeting criteria
// 3. Gradually migrate historical data
```

## Step-by-Step Migration

### Step 1: Add Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("app.cash.tempest:tempest-hybrid:VERSION")
    implementation("com.amazonaws:aws-java-sdk-s3:VERSION")
}
```

### Step 2: Configure Hybrid Storage

```kotlin
@Configuration
class HybridStorageConfig {

    @Bean
    fun s3Client(): AmazonS3 {
        return AmazonS3ClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .build()
    }

    @Bean
    fun s3Executor(): ExecutorService {
        return Executors.newFixedThreadPool(10)
    }

    @Bean
    fun hybridDynamoDbClient(
        originalClient: DynamoDbClient,
        s3Client: AmazonS3,
        s3Executor: ExecutorService
    ): DynamoDbClient {
        val config = HybridConfig(
            s3Config = HybridConfig.S3Config(
                bucketName = "my-archive-bucket",
                keyPrefix = "dynamodb/",
                region = "us-east-1"
            ),
            retryConfig = HybridConfig.RetryConfig(
                enabled = true,
                maxAttempts = 3,
                initialDelayMs = 100,
                maxDelayMs = 5000
            )

        val factory = HybridDynamoDbFactory(
            s3Client = s3Client,
            objectMapper = ObjectMapper(),
            hybridConfig = config,
            s3Executor = s3Executor
        )

        return factory.wrapDynamoDbClient(originalClient)
    }
}
```

### Step 3: Update Your Application Code

**Before Migration:**
```kotlin
@Component
class TransactionRepository(
    private val dynamoDbClient: DynamoDbClient
) {
    fun getTransaction(id: String): Transaction? {
        // Standard DynamoDB query
        val response = dynamoDbClient.query(...)
        return response.items().firstOrNull()?.let {
            deserialize(it)
        }
    }
}
```

**After Migration:**
```kotlin
@Component
class TransactionRepository(
    private val dynamoDbClient: DynamoDbClient  // Now hybrid-aware!
) {
    fun getTransaction(id: String): Transaction? {
        // Same code - no changes needed!
        // Library handles S3 hydration transparently
        val response = dynamoDbClient.query(...)
        return response.items().firstOrNull()?.let {
            deserialize(it)
        }
    }
}
```

### Step 4: Deploy Read Support

Deploy your application with the hybrid client. At this point:
- ✅ Can read regular DynamoDB items
- ✅ Can read S3-backed items (if any exist)
- ❌ Not yet archiving to S3

## Archival Process Implementation

The library handles reading from S3, but you need to implement the archival process:

### Example Archival Job

```kotlin
@Component
class S3ArchivalJob(
    private val dynamoDbClient: DynamoDbClient,
    private val s3Client: AmazonS3,
    private val objectMapper: ObjectMapper
) {

    @Scheduled(cron = "0 0 2 * * *")  // Run at 2 AM daily
    fun archiveOldTransactions() {
        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)

        // Query for old items
        val oldItems = queryOldTransactions(thirtyDaysAgo)

        for (item in oldItems) {
            try {
                // 1. Write full item to S3
                val s3Key = generateS3Key(item)
                val jsonData = objectMapper.writeValueAsString(item)
                val compressed = gzipCompress(jsonData)

                s3Client.putObject(
                    PutObjectRequest()
                        .withBucketName("my-archive-bucket")
                        .withKey(s3Key)
                        .withMetadata(metadata),
                    compressed
                )

                // 2. Replace DynamoDB item with pointer
                val pointerItem = mapOf(
                    "pk" to item["pk"],
                    "sk" to item["sk"],
                    "_s3_pointer" to AttributeValue.builder()
                        .s("s3://$s3Key").build()
                )

                dynamoDbClient.putItem(
                    PutItemRequest.builder()
                        .tableName("transactions")
                        .item(pointerItem)
                        .build()
                )

                logger.info("Archived item to S3: $s3Key")

            } catch (e: Exception) {
                logger.error("Failed to archive item", e)
                // Handle error - maybe retry or alert
            }
        }
    }

    private fun generateS3Key(item: Map<String, AttributeValue>): String {
        // Option 1: Use S3KeyGenerator with config (recommended)
        return S3KeyGenerator.generateS3Key(
            item,
            "{tableName}/{partitionKey}/{sortKey}",
            "transactions",
            hybridConfig  // Uses keyPrefix from config
        )

        // Option 2: Manual generation (legacy)
        // val pk = item["pk"]?.s() ?: throw IllegalStateException("Missing pk")
        // val sk = item["sk"]?.s() ?: throw IllegalStateException("Missing sk")
        // val prefix = hybridConfig.s3Config.keyPrefix
        // return "${prefix}transactions/$pk/$sk.json.gz"
    }
}
```

### Archival Criteria

Consider archiving items that are:

1. **Time-based**: Older than X days
2. **Size-based**: Larger than X KB
3. **Access-based**: Not accessed in X days
4. **Type-based**: Specific item types (e.g., audit logs)

## Testing Strategy

### Unit Tests

```kotlin
@Test
fun `handles S3 pointer hydration correctly`() {
    // Given: Item with S3 pointer
    val pointerItem = mapOf(
        "pk" to AttributeValue.builder().s("TEST#1").build(),
        "_s3_pointer" to AttributeValue.builder()
            .s("s3://test/data.json").build()
    )

    // When: S3 returns full data
    whenever(s3Client.getObject(any(), any())).thenReturn(fullData)

    // Then: Hydrated item returned
    val result = hybridClient.getItem(request)
    assertThat(result.item()).containsAllExpectedFields()
}
```

### Integration Tests

```kotlin
@Test
fun `end-to-end archival and retrieval`() {
    // 1. Insert regular item
    dynamoDbClient.putItem(regularItem)

    // 2. Run archival process
    archivalJob.archiveItem(itemId)

    // 3. Verify pointer in DynamoDB
    val dbItem = dynamoDbClient.getItem(itemId)
    assertThat(dbItem["_s3_pointer"]).isNotNull()

    // 4. Query through hybrid client
    val retrieved = hybridClient.getItem(itemId)
    assertThat(retrieved).isEqualTo(regularItem)
}
```

### Performance Tests

```kotlin
@Test
fun `parallel hydration performance`() {
    // Setup: 50 S3 pointers
    val pointers = (1..50).map { createPointer(it) }

    // Measure: Sequential vs parallel
    val sequentialTime = measureTime {
        sequentialClient.query(request)
    }

    val parallelTime = measureTime {
        parallelClient.query(request)
    }

    // Assert: Parallel is faster
    assertThat(parallelTime).isLessThan(sequentialTime / 5)
}
```

## Rollback Plan

If issues arise, you can rollback safely:

### Option 1: Disable S3 Hydration

```kotlin
// Quick disable - revert to returning pointers
@Bean
fun dynamoDbClient(): DynamoDbClient {
    return originalDynamoDbClient  // Remove hybrid wrapper
}
```

### Option 2: Re-hydrate to DynamoDB

```kotlin
// Emergency re-hydration job
fun rehydrateFromS3() {
    val pointerItems = scanForPointers()

    for (pointer in pointerItems) {
        val fullData = loadFromS3(pointer["_s3_pointer"])
        dynamoDbClient.putItem(fullData)  // Restore full item
    }
}
```

## Post-Migration Monitoring

### Key Metrics

```kotlin
@Component
class HybridStorageMetrics(
    private val meterRegistry: MeterRegistry
) {

    fun recordHydration(success: Boolean, latency: Long) {
        meterRegistry.counter("s3.hydration",
            "status", if (success) "success" else "failure"
        ).increment()

        meterRegistry.timer("s3.hydration.latency")
            .record(latency, TimeUnit.MILLISECONDS)
    }

    fun recordArchival(success: Boolean) {
        meterRegistry.counter("s3.archival",
            "status", if (success) "success" else "failure"
        ).increment()
    }
}
```

### Alerts to Configure

1. **S3 Hydration Failures** > 1% - Potential S3 issues
2. **Hydration P99 Latency** > 500ms - Performance degradation
3. **Archival Job Failures** - Data not being archived
4. **DynamoDB Throttling** - May need to adjust archival rate

### Cost Monitoring

```kotlin
// Track storage costs
val dynamoDbStorage = cloudWatch.getMetric("ConsumedReadCapacityUnits")
val s3Storage = cloudWatch.getMetric("BucketSizeBytes")

val monthlySavings = calculateSavings(dynamoDbStorage, s3Storage)
logger.info("Estimated monthly savings: $$monthlySavings")
```

## Common Migration Patterns

### Pattern 1: Archive by Age

```kotlin
// Archive transactions older than 30 days
val cutoffDate = Instant.now().minus(30, ChronoUnit.DAYS)
archiveItemsOlderThan(cutoffDate)
```

### Pattern 2: Archive by Size

```kotlin
// Archive items larger than 100KB
archiveItemsLargerThan(100 * 1024)
```

### Pattern 3: Archive by Type

```kotlin
// Archive specific item types
archiveItemsOfType("AUDIT_LOG", "HISTORICAL_SNAPSHOT")
```

### Pattern 4: Archive by Access Pattern

```kotlin
// Archive items not accessed in 90 days
archiveInfrequentlyAccessedItems(90)
```

## Troubleshooting Migration Issues

### Issue: Slow Hydration Performance

**Symptoms**: High latency when reading S3-backed items

**Solutions**:
1. Increase thread pool size
2. Optimize S3 object sizes (aim for < 1MB)
3. Use S3 Transfer Acceleration
4. Consider caching frequently accessed items

### Issue: Increased Error Rate

**Symptoms**: Failures reading S3-backed items

**Solutions**:
1. Check IAM permissions
2. Verify S3 bucket policy
3. Monitor S3 throttling metrics
4. Enable retry logic via `RetryConfig` if not already enabled

### Issue: Higher Than Expected Costs

**Symptoms**: S3 API costs exceeding projections

**Solutions**:
1. Batch archival operations
2. Use S3 Intelligent-Tiering
3. Implement caching layer
4. Optimize query patterns

## Best Practices

1. **Start Small**: Begin with non-critical data
2. **Monitor Closely**: Watch metrics during initial rollout
3. **Gradual Rollout**: Increase archival rate gradually
4. **Document Decisions**: Record archival criteria and thresholds
5. **Plan Capacity**: Ensure S3 bucket has appropriate limits
6. **Test Recovery**: Verify you can restore from S3 if needed
7. **Automate**: Use scheduled jobs for archival
8. **Alert Early**: Set up proactive monitoring

## Next Steps

After successful migration:

1. **Optimize**: Fine-tune thread pool and archival criteria
2. **Expand**: Apply to additional tables/datasets
3. **Automate**: Implement intelligent archival policies
4. **Monitor**: Track cost savings and performance metrics

## Support

For issues or questions:
- Check the [README](README.md) for configuration details
- Review the [Design Document](../TEMPEST_HYBRID_DESIGN.md) for architecture details
- Open an issue in the Tempest repository