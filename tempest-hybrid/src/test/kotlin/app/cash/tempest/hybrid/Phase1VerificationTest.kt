/*
 * Copyright 2024 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.tempest.hybrid

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import io.mockk.mockk

/**
 * Verification test for Phase 1 implementation.
 * This test ensures all the core components are in place and can be instantiated.
 */
class Phase1VerificationTest {

  @Test
  fun `can create HybridLogicalDb with user-provided S3Client`() {
    // Given: User provides their own S3Client and configuration
    val s3Client = mockk<AmazonS3>()
    val regularDb = mockk<app.cash.tempest.LogicalDb>()
    val objectMapper = ObjectMapper().registerModule(KotlinModule())

    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(
        bucketName = "test-bucket",
        keyPrefix = "archive"
      ),
      archivalConfig = HybridConfig.ArchivalConfig(
        enabled = true,
        batchSize = 25
      ),
      performanceConfig = HybridConfig.PerformanceConfig(
        parallelS3Reads = 1  // No parallelism in Phase 1
      )
    )

    // When: Creating HybridLogicalDb
    val hybridDb = HybridLogicalDb.create(
      regularDb = regularDb,
      s3Client = s3Client,
      hybridConfig = config,
      objectMapper = objectMapper
    )

    // Then: HybridLogicalDb is created successfully
    assertNotNull(hybridDb)
    assertEquals(config, hybridDb.hybridConfig)

    // Clean up
    hybridDb.close()
  }

  @Test
  fun `configuration is properly structured for Phase 1`() {
    // Given: Phase 1 configuration
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(
        bucketName = "my-archive-bucket",
        keyPrefix = "dynamodb-archives"
      ),
      archivalConfig = HybridConfig.ArchivalConfig(
        enabled = true,
        batchSize = 50
      )
    )

    // Then: Configuration has expected defaults
    assertEquals("my-archive-bucket", config.s3Config.bucketName)
    assertEquals("dynamodb-archives", config.s3Config.keyPrefix)
    assertNull(config.s3Config.region)  // Optional in Phase 1

    assertTrue(config.archivalConfig.enabled)
    assertEquals(50, config.archivalConfig.batchSize)

    // Performance config defaults to no parallelism in Phase 1
    assertEquals(1, config.performanceConfig.parallelS3Reads)
  }

  @Test
  fun `S3 keys are deterministic and do not include timestamps`() {
    // Given: An item with keys
    @HybridTable(
      archiveAfterDays = 180,
      s3KeyTemplate = "{tableName}/{partitionKey}/{sortKey}"
    )
    data class TestItem(
      @com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
      val userId: String,
      @com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
      val version: String,
      val data: String
    )

    val item1 = TestItem("USER_123", "v1", "data1")
    val item2 = TestItem("USER_123", "v1", "data2")

    // When: Generating S3 keys
    val key1 = S3KeyGenerator.generateS3Key(item1, "{tableName}/{partitionKey}/{sortKey}", "users")
    val key2 = S3KeyGenerator.generateS3Key(item2, "{tableName}/{partitionKey}/{sortKey}", "users")

    // Then: Keys are identical (deterministic)
    assertEquals(key1, key2)
    assertEquals("users/USER_123/v1.json.gz", key1)

    // And: No timestamp in the key
    assertFalse(key1.contains("2024"))  // No date
    assertFalse(key1.contains(":"))     // No time
  }

  @Test
  fun `HybridTable annotation has sensible defaults`() {
    // Given: A table with HybridTable annotation using defaults
    @HybridTable
    class DefaultTable

    // When: Getting annotation
    val annotation = DefaultTable::class.annotations
      .filterIsInstance<HybridTable>()
      .firstOrNull()

    // Then: Defaults are sensible
    assertNotNull(annotation)
    assertEquals(180, annotation?.archiveAfterDays)  // 6 months default
    assertEquals("{tableName}/{partitionKey}/{sortKey}", annotation?.s3KeyTemplate)
  }

  @Test
  fun `all required annotations are available`() {
    // Verify all annotations exist and can be used
    assertDoesNotThrow {
      // HybridTable annotation
      @HybridTable(archiveAfterDays = 365)
      class TestTable

      // ArchivalTimestamp annotation
      class TestItem(
        @ArchivalTimestamp
        val createdAt: java.time.Instant
      )

      // Check they can be found at runtime
      val hybridAnnotation = TestTable::class.annotations
        .filterIsInstance<HybridTable>()
        .first()

      val timestampAnnotation = TestItem::class.members
        .find { it.name == "createdAt" }
        ?.annotations
        ?.filterIsInstance<ArchivalTimestamp>()
        ?.firstOrNull()

      assertNotNull(hybridAnnotation)
      assertNotNull(timestampAnnotation)
    }
  }

  @Test
  fun `ArchivalResult provides useful summary`() {
    // Given: An archival result
    val result = ArchivalResult(
      itemsProcessed = 1000,
      itemsArchived = 750,
      errors = listOf(
        "Failed to archive item USER_001: S3 timeout",
        "Failed to archive item USER_002: Invalid data"
      )
    )

    // Then: Properties are calculated correctly
    assertEquals(250, result.itemsSkipped)
    assertFalse(result.success)  // Has errors

    // And: Summary string is useful
    val summary = result.toSummaryString()
    assertTrue(summary.contains("Items processed: 1000"))
    assertTrue(summary.contains("Items archived: 750"))
    assertTrue(summary.contains("Items skipped: 250"))
    assertTrue(summary.contains("Errors: 2"))
  }
}

/**
 * Summary of Phase 1 Implementation Status:
 *
 * ✅ COMPLETE:
 * - HybridLogicalDb interface with create() methods
 * - HybridLogicalDbImpl with S3Client support
 * - HybridConfig with proper Phase 1 fields
 * - HybridDbProxy for database-level interception
 * - HybridTableProxy for table-level interception
 * - HybridViewProxy for view-level operations (load/query/scan)
 * - S3KeyGenerator with deterministic key generation
 * - ArchivalService for archiving old data
 * - All required annotations (HybridTable, ArchivalTimestamp, HybridInlineView)
 * - ArchivalResult for operation results
 *
 * 🎯 READY FOR TESTING:
 * - Load single items with S3 hydration
 * - Query with automatic pointer hydration
 * - Scan with automatic pointer hydration
 * - Manual archival execution
 * - Deterministic S3 key generation
 *
 * 📝 USAGE:
 * ```kotlin
 * // Create hybrid DB
 * val hybridDb = HybridLogicalDb.create(
 *   regularDb = myExistingDb,
 *   s3Client = myS3Client,
 *   hybridConfig = config,
 *   objectMapper = objectMapper
 * )
 *
 * // Use normally - transparent S3 integration
 * val item = hybridDb.myTable.myView.load(key)  // Fetches from S3 if archived
 * val items = hybridDb.myTable.myView.query(condition)  // Hydrates pointers
 *
 * // Manual archival
 * val result = hybridDb.archiveOldData()
 * ```
 */