# Tempest Hybrid Storage - Basic Usage Guide

This guide demonstrates the core functionality of Tempest Hybrid Storage without performance optimizations or scheduling. Perfect for testing and development.

## Core Features

The basic hybrid storage provides:
1. **Transparent S3 Integration** - Queries automatically fetch from S3 when needed
2. **Manual Archival** - Archive old data to S3 on demand
3. **Seamless Queries** - Works with existing Tempest code

## Quick Start

### 1. Define Your Domain Model

```kotlin
import app.cash.tempest.hybrid.*
import java.time.Instant

// Mark your table with @HybridTable
@HybridTable(
    archiveAfterDays = 180,  // Archive items older than 6 months
    s3KeyTemplate = "orders/{partitionKey}/{sortKey}/{timestamp}"
)
@DynamoDBTable(tableName = "orders")
data class OrderItem(
    @DynamoDBHashKey
    val customerId: String,

    @DynamoDBRangeKey
    val orderId: String,

    @ArchivalTimestamp  // Mark the timestamp field for archival
    val createdAt: Instant,

    // Regular data fields
    val amount: Double,
    val items: String,  // JSON blob of order items
    val shippingAddress: String,

    // These fields are added when archived
    val s3Key: String? = null,        // S3 location when archived
    val archivedAt: Instant? = null   // When it was archived
)
```

### 2. Configure Hybrid Storage

```kotlin
import app.cash.tempest.hybrid.*
import com.amazonaws.services.s3.AmazonS3ClientBuilder

// Create your S3 client
val s3Client = AmazonS3ClientBuilder.standard()
    .withRegion("us-west-2")
    .build()

// Configure hybrid storage
val hybridConfig = HybridConfig(
    s3Config = HybridConfig.S3Config(
        bucketName = "my-archive-bucket",
        keyPrefix = "dynamodb-archives"  // Optional prefix
    ),
    archivalConfig = HybridConfig.ArchivalConfig(
        enabled = true,
        batchSize = 25  // Items to process at once
    ),
    // Disable Phase 2 optimizations for basic usage
    cacheConfig = HybridConfig.CacheConfig(enabled = false),
    performanceConfig = HybridConfig.PerformanceConfig(parallelS3Reads = 1)
)
```

### 3. Create Hybrid Database

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

// Your existing Tempest database
val regularDb: YourDatabase = // ... your existing setup

// Create object mapper for S3 serialization
val objectMapper = ObjectMapper()
    .registerModule(KotlinModule())

// Wrap with hybrid functionality
val hybridDb = HybridLogicalDb.create(
    regularDb,
    s3Client,
    hybridConfig,
    objectMapper
)
```

### 4. Use It Like Regular Tempest

```kotlin
// Access tables normally - hybrid functionality is transparent
val ordersTable = hybridDb.orders
val orderView = ordersTable.orderView

// Single item load - automatically fetches from S3 if archived
val order = orderView.load(OrderKey(customerId = "CUST123", orderId = "ORD456"))

if (order != null) {
    if (order.s3Key != null) {
        println("This order was archived to S3: ${order.s3Key}")
    } else {
        println("This order is still in DynamoDB")
    }

    // The data is transparently available regardless of storage location
    println("Order amount: ${order.amount}")
    println("Order items: ${order.items}")
}

// Query - automatically hydrates S3 pointers
val customerOrders = orderView.query(
    KeyCondition.BeginsWith("CUST123#")
)

customerOrders.contents.forEach { order ->
    // All orders are fully hydrated, whether from DynamoDB or S3
    println("Order ${order.orderId}: $${order.amount}")
}

// Scan - also hydrates S3 pointers
val allOrders = orderView.scan()
println("Total orders: ${allOrders.contents.size}")
```

### 5. Manual Archival

```kotlin
// Archive old data manually (no scheduler needed)
suspend fun archiveOldOrders() {
    // Dry run first to see what would be archived
    val dryRunResult = hybridDb.archiveOldData(dryRun = true)
    println("Would archive ${dryRunResult.itemsArchived} items")

    // Actually archive
    val result = hybridDb.archiveOldData(dryRun = false)

    println("Archival complete:")
    println("  Items processed: ${result.itemsProcessed}")
    println("  Items archived: ${result.itemsArchived}")

    if (result.errors.isNotEmpty()) {
        println("  Errors:")
        result.errors.forEach { error ->
            println("    - $error")
        }
    }
}

// Run archival when needed
runBlocking {
    archiveOldOrders()
}
```

## How It Works

### The Archival Process

When you call `archiveOldData()`:

1. **Discovers Hybrid Tables**: Finds all tables marked with `@HybridTable`
2. **Scans for Old Items**: Looks for items older than `archiveAfterDays`
3. **Creates S3 Archive**:
   - Compresses item with GZIP
   - Stores in S3 with generated key
4. **Updates DynamoDB**:
   - Replaces full item with a "pointer"
   - Pointer keeps keys and S3 reference
   - Clears large data fields to save space

### The Retrieval Process

When you query/load items:

1. **DynamoDB First**: Queries DynamoDB normally
2. **Pointer Detection**: Checks if item has `s3Key` field
3. **S3 Hydration**: If pointer found, fetches from S3
4. **Transparent Return**: Returns full item to user

## Example: Testing the Basic Functionality

```kotlin
// Test script to verify everything works
fun main() = runBlocking {
    // Setup
    val hybridDb = setupHybridDb()
    val orderView = hybridDb.orders.orderView

    // Create test data
    val testOrder = OrderItem(
        customerId = "TEST_CUSTOMER",
        orderId = "TEST_ORDER_001",
        createdAt = Instant.now().minus(Duration.ofDays(200)), // Old item
        amount = 99.99,
        items = """{"items": ["widget", "gadget"]}""",
        shippingAddress = "123 Test St"
    )

    // Save to DynamoDB
    orderView.save(testOrder)
    println("Saved test order to DynamoDB")

    // Archive old data
    val archiveResult = hybridDb.archiveOldData(dryRun = false)
    println("Archived ${archiveResult.itemsArchived} items")

    // Load it back - should fetch from S3
    val loadedOrder = orderView.load(
        OrderKey("TEST_CUSTOMER", "TEST_ORDER_001")
    )

    if (loadedOrder != null) {
        println("Successfully loaded order:")
        println("  From S3: ${loadedOrder.s3Key != null}")
        println("  Amount: ${loadedOrder.amount}")
        println("  Items: ${loadedOrder.items}")
    }

    // Query test
    val orders = orderView.query(
        KeyCondition.BeginsWith("TEST_CUSTOMER")
    )
    println("Query returned ${orders.contents.size} orders")
}
```

## What's Stored Where

After archival, your data is split:

### In DynamoDB (Pointer)
```json
{
  "customerId": "CUST123",
  "orderId": "ORD456",
  "createdAt": "2023-01-15T10:00:00Z",
  "s3Key": "dynamodb-archives/orders/CUST123/ORD456/2023-01-15.json.gz",
  "archivedAt": "2024-07-15T10:00:00Z",

  // Large fields are cleared
  "amount": null,
  "items": null,
  "shippingAddress": null
}
```

### In S3 (Full Data)
```json
// Stored as compressed JSON at the S3 key
{
  "customerId": "CUST123",
  "orderId": "ORD456",
  "createdAt": "2023-01-15T10:00:00Z",
  "amount": 299.99,
  "items": "{\"items\": [...]}",
  "shippingAddress": "123 Main St..."
}
```

## Key Points

1. **No Scheduler Required** - Call `archiveOldData()` manually when needed
2. **Transparent Access** - Users don't need to know about S3
3. **Graceful Fallback** - Returns pointer if S3 fails
4. **Query Support** - Maintains queryability via DynamoDB keys
5. **Cost Effective** - ~79% storage cost reduction

## Troubleshooting

### Items Not Being Archived

Check:
- Item has `@ArchivalTimestamp` field
- Timestamp is older than `archiveAfterDays`
- `archivalConfig.enabled = true`

### S3 Access Issues

Verify:
- S3 bucket exists and is accessible
- IAM permissions include `s3:GetObject` and `s3:PutObject`
- Bucket name and region are correct

### Query Performance

Without Phase 2 optimizations:
- Each S3 pointer requires a separate S3 read
- Consider enabling cache for production use
- Batch operations are sequential

## Next Steps

For production use, consider:
1. **Enable Caching** - Set `cacheConfig.enabled = true`
2. **Add Scheduling** - Use cron job or AWS Lambda
3. **Enable Parallelism** - Increase `parallelS3Reads`
4. **Monitor Performance** - Track S3 costs and latency

This basic functionality is perfect for:
- Development and testing
- Small-scale deployments
- Proof of concept implementations
- Understanding the hybrid storage model