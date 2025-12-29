# Tempest-Hybrid: DynamoDB + S3 Hybrid Storage

A library that enables transparent S3-backed storage for DynamoDB, allowing applications to store large or cold data in S3 while maintaining DynamoDB's query capabilities through automatic hydration.

## Features

- **Transparent S3 Hydration**: Automatically retrieves S3-stored data when querying DynamoDB
- **Type Safety**: Preserves Tempest's type-safe DynamoDB operations
- **Performance**: Parallel S3 fetching for multiple items
- **Compatibility**: Works with existing DynamoDB queries without modification
- **Cost Optimization**: Store cold/large data in S3 (significantly cheaper than DynamoDB)

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("app.cash.tempest:tempest-hybrid:VERSION")
}
```

## Quick Start

### 1. Basic Setup

```kotlin
import app.cash.tempest.hybrid.*
import java.util.concurrent.Executors

// Create your executor for parallel S3 reads
val s3Executor = Executors.newFixedThreadPool(10)

// Configure hybrid storage
val hybridConfig = HybridConfig(
    s3Config = HybridConfig.S3Config(
        bucketName = "my-data-bucket",
        keyPrefix = "dynamodb-archive/",  // Optional
        region = "us-east-1"              // Optional
    )
)

// Create the factory
val hybridFactory = HybridDynamoDbFactory(
    s3Client = amazonS3Client,
    objectMapper = objectMapper,
    hybridConfig = hybridConfig,
    s3Executor = s3Executor
)

// Wrap your existing DynamoDB client
val hybridDynamoDbClient = hybridFactory.wrapDynamoDbClient(originalDynamoDbClient)

// Use the hybrid client with Tempest as normal
val db = LogicalDb(DynamoDBEnhanced.builder()
    .dynamoDbClient(hybridDynamoDbClient)
    .build())
```

### 2. S3 Pointer Format

When items are stored in S3 (by your external archival process), represent them in DynamoDB with this pointer structure:

```json
{
  "pk": "USER#123",
  "sk": "TRANSACTION#2024-01-15",
  "_s3_pointer": "s3://path/to/data.json.gz"
}
```

The library will automatically detect these pointers and hydrate the full data from S3.

### 3. Executor Lifecycle Management

The library provides two options for executor management:

#### Option A: Use the Convenience Factory (Recommended)

```kotlin
// Creates an executor with automatic shutdown on JVM exit
val s3Executor = HybridDynamoDbFactory.createS3Executor(
    threadCount = 10,
    threadNamePrefix = "s3-hydration"
)

// Features:
// - Daemon threads (won't prevent JVM shutdown)
// - Automatic shutdown hook
// - Descriptive thread names
// - No manual cleanup needed
```

#### Option B: Manage Your Own Executor

```kotlin
// Create your own executor
val s3Executor = Executors.newFixedThreadPool(10)

// IMPORTANT: You must shut it down properly
Runtime.getRuntime().addShutdownHook(Thread {
    s3Executor.shutdown()
    if (!s3Executor.awaitTermination(30, TimeUnit.SECONDS)) {
        s3Executor.shutdownNow()
    }
})
```

**⚠️ Warning**: The library will log a warning if executors are not properly shutdown. This helps detect thread leaks that could prevent JVM shutdown.

## How It Works

1. **Query Interception**: The library wraps your DynamoDB client to intercept Query, Scan, and GetItem operations
2. **Pointer Detection**: Identifies items with `_s3_pointer` attributes
3. **S3 Hydration**: Fetches full data from S3 (in parallel for multiple items)
4. **Transparent Replacement**: Replaces pointer items with hydrated data before returning to your application

## Configuration

### Executor Configuration

Choose the right executor based on your workload:

```kotlin
// Fixed thread pool - predictable resource usage
val s3Executor = Executors.newFixedThreadPool(10)

// Cached thread pool - scales with demand
val s3Executor = Executors.newCachedThreadPool()

// Virtual threads (Java 21+) - ideal for I/O operations
val s3Executor = Executors.newVirtualThreadPerTaskExecutor()

// Sequential mode - useful for debugging
val s3Executor = null  // Pass null for sequential execution
```

### Thread Pool Sizing Guidelines

| Workload Type | Recommended Threads | Rationale |
|--------------|-------------------|-----------|
| Light (< 10 items/query) | 5 | Minimize resource usage |
| Medium (10-50 items/query) | 10 | Balance performance and resources |
| Heavy (> 50 items/query) | 20 | Maximize parallelism |
| Batch Operations | 30-50 | High throughput for migrations |

## Performance

### Parallel vs Sequential Hydration

With 10 threads and 50ms S3 latency:

| Items | Sequential | Parallel | Improvement |
|-------|-----------|----------|-------------|
| 10 | 500ms | 50ms | 10x |
| 20 | 1,000ms | 100ms | 10x |
| 50 | 2,500ms | 250ms | 10x |
| 100 | 5,000ms | 500ms | 10x |

## Spring Boot Integration

```kotlin
@Configuration
class DynamoDbConfig {

    @Bean
    fun s3Executor(): ExecutorService {
        return ThreadPoolExecutor(
            5,  // core pool size
            20, // maximum pool size
            60L, TimeUnit.SECONDS, // keep-alive
            LinkedBlockingQueue(100),
            ThreadFactoryBuilder()
                .setNameFormat("s3-hydration-%d")
                .build()
        )
    }

    @Bean
    fun hybridDynamoDbClient(
        dynamoDbClient: DynamoDbClient,
        s3Client: AmazonS3,
        objectMapper: ObjectMapper,
        @Value("\${s3.bucket}") bucketName: String,
        s3Executor: ExecutorService
    ): DynamoDbClient {
        val config = HybridConfig(
            s3Config = HybridConfig.S3Config(bucketName = bucketName)
        )

        val factory = HybridDynamoDbFactory(
            s3Client = s3Client,
            objectMapper = objectMapper,
            hybridConfig = config,
            s3Executor = s3Executor
        )

        return factory.wrapDynamoDbClient(dynamoDbClient)
    }

    @PreDestroy
    fun cleanup() {
        s3Executor.shutdown()
    }
}
```

## Data Format

### DynamoDB JSON Format

The library supports DynamoDB's native JSON format:

```json
{
  "pk": {"S": "USER#123"},
  "sk": {"S": "TRANSACTION#2024"},
  "amount": {"N": "100.50"},
  "items": {"L": [
    {"S": "item1"},
    {"S": "item2"}
  ]},
  "metadata": {"M": {
    "category": {"S": "food"},
    "priority": {"N": "1"}
  }}
}
```

### Regular JSON Format

Also supports regular JSON (automatically converted):

```json
{
  "pk": "USER#123",
  "sk": "TRANSACTION#2024",
  "amount": 100.50,
  "items": ["item1", "item2"],
  "metadata": {
    "category": "food",
    "priority": 1
  }
}
```

## Error Handling

The library implements graceful degradation:

- **S3 Read Failures**: Returns the original pointer item (application may handle or fail)
- **JSON Parse Errors**: Logs error and returns pointer item
- **Network Issues**: No built-in retry (add your own retry logic if needed)

## Monitoring

### Key Metrics to Track

```kotlin
// Expose these metrics in your application
- S3 hydration success rate
- S3 read latency (P50, P99)
- Number of items hydrated per query
- Executor queue depth
- Thread pool utilization
```

### Logging

The library uses SLF4J for logging:

```xml
<!-- Set appropriate log levels -->
<logger name="app.cash.tempest.hybrid" level="INFO"/>
```

## Limitations

1. **Read-Only Hydration**: This library only handles the read path. Writing to S3 and creating pointers must be done separately.
2. **No Caching**: Does not cache hydrated items (add application-level caching if needed)
3. **No Retry Logic**: S3 failures are not retried (implement at application level)
4. **Size Limits**: S3 objects should be reasonable size for in-memory processing

## Security Considerations

1. **IAM Permissions**: Ensure your application has proper S3 read permissions
2. **Encryption**: Use S3 encryption at rest (SSE-S3 or SSE-KMS)
3. **Network**: Consider using VPC endpoints for S3 access
4. **Sensitive Data**: S3 pointer paths are visible in DynamoDB

## Troubleshooting

### Common Issues

**Issue**: High latency for queries with many S3 pointers
- **Solution**: Increase thread pool size or optimize S3 object sizes

**Issue**: Out of memory errors
- **Solution**: Reduce thread pool size or process large results in batches

**Issue**: S3 throttling errors
- **Solution**: Reduce concurrency or implement exponential backoff

## Contributing

See the main Tempest repository for contribution guidelines.

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.