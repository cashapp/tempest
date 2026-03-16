/**
 * Tempest-Hybrid: Transparent S3-backed storage for DynamoDB
 *
 * <p>This library enables cost-effective storage of large or infrequently accessed DynamoDB items
 * in Amazon S3 while maintaining full query compatibility through automatic hydration.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Transparent S3 hydration during DynamoDB queries</li>
 *   <li>Parallel fetching for improved performance</li>
 *   <li>Type-safe operations with Tempest</li>
 *   <li>Configurable executor management</li>
 *   <li>Graceful error handling</li>
 * </ul>
 *
 * <h2>Architecture:</h2>
 * <pre>
 * Application
 *     ↓
 * S3AwareDynamoDbClient (intercepts operations)
 *     ↓
 * DynamoDB ← → S3
 * (pointers)   (full data)
 * </pre>
 *
 * <h2>Basic Usage:</h2>
 * <pre>{@code
 * // Create executor for parallel S3 reads
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 *
 * // Configure hybrid storage
 * HybridConfig config = new HybridConfig(
 *     new S3Config("my-bucket", "prefix/", "us-east-1")
 * );
 *
 * // Create factory
 * HybridDynamoDbFactory factory = new HybridDynamoDbFactory(
 *     s3Client, objectMapper, config, executor
 * );
 *
 * // Wrap existing DynamoDB client
 * DynamoDbClient hybridClient = factory.wrapDynamoDbClient(dynamoDbClient);
 * }</pre>
 *
 * <h2>S3 Pointer Format:</h2>
 * <p>Items stored in S3 are represented in DynamoDB as pointers:</p>
 * <pre>{@code
 * {
 *   "pk": "USER#123",
 *   "sk": "TRANSACTION#456",
 *   "_s3_pointer": "s3://path/to/data.json.gz"
 * }
 * }</pre>
 *
 * <h2>Important Notes:</h2>
 * <ul>
 *   <li>This library handles READ operations only</li>
 *   <li>Archival to S3 must be implemented separately</li>
 *   <li>Caller must manage executor lifecycle</li>
 *   <li>No built-in caching or retry logic</li>
 * </ul>
 *
 * @see app.cash.tempest.hybrid.S3AwareDynamoDbClient
 * @see app.cash.tempest.hybrid.HybridDynamoDbFactory
 * @see app.cash.tempest.hybrid.HybridConfig
 */
package app.cash.tempest.hybrid;