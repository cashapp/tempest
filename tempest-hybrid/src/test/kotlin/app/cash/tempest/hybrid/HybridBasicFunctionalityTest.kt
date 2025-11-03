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

import app.cash.tempest.*
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Test the basic functionality of hybrid storage without performance optimizations.
 * This test verifies:
 * 1. S3 configuration is properly used
 * 2. Single load operations work with S3 pointers
 * 3. Query operations hydrate S3 pointers
 * 4. Scan operations hydrate S3 pointers
 */
class HybridBasicFunctionalityTest {

    // Test domain objects
    @HybridTable(
        archiveAfterDays = 180,
        s3KeyTemplate = "users/{partitionKey}/{sortKey}/{timestamp}"
    )
    @DynamoDBTable(tableName = "users")
    data class UserItem(
        @DynamoDBHashKey
        val userId: String,

        @DynamoDBRangeKey
        val version: String,

        @ArchivalTimestamp
        val createdAt: Instant,

        val name: String,
        val email: String,
        val data: String,  // Large field that gets archived

        // Fields added when archived
        val s3Key: String? = null,
        val archivedAt: Instant? = null
    )

    data class UserKey(
        val userId: String,
        val version: String
    )

    interface UserTable : LogicalTable<UserItem> {
        val userView: InlineView<UserKey, UserItem>
    }

    interface TestDb : LogicalDb {
        val users: UserTable
    }

    // Test setup
    private lateinit var s3Client: AmazonS3
    private lateinit var regularDb: TestDb
    private lateinit var hybridDb: HybridLogicalDb
    private lateinit var objectMapper: ObjectMapper
    private lateinit var config: HybridConfig

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper().registerModule(KotlinModule())

        // Mock S3 client
        s3Client = mockk()

        // Mock regular database
        regularDb = mockk()

        // Create hybrid config
        config = HybridConfig(
            s3Config = HybridConfig.S3Config(
                bucketName = "test-bucket",
                keyPrefix = "archived"
            ),
            archivalConfig = HybridConfig.ArchivalConfig(
                enabled = true,
                batchSize = 25
            ),
            // Disable Phase 2 optimizations for this test
            cacheConfig = HybridConfig.CacheConfig(enabled = false),
            performanceConfig = HybridConfig.PerformanceConfig(parallelS3Reads = 1)
        )

        // Create hybrid database
        hybridDb = HybridLogicalDb.create(
            regularDb,
            s3Client,
            config,
            objectMapper
        )
    }

    @Test
    fun `test single load - item in DynamoDB (not archived)`() {
        // Given: An item that exists in DynamoDB with full data (not a pointer)
        val key = UserKey("user123", "v1")
        val fullItem = UserItem(
            userId = "user123",
            version = "v1",
            createdAt = Instant.now(),
            name = "John Doe",
            email = "john@example.com",
            data = "Some user data",
            s3Key = null  // Not archived
        )

        val mockTable = mockk<UserTable>()
        val mockView = mockk<InlineView<UserKey, UserItem>>()

        every { regularDb.users } returns mockTable
        every { mockTable.userView } returns mockView
        every { mockView.load(key) } returns fullItem

        // When: Loading the item through hybrid view
        val hybridTable = hybridDb.users
        val hybridView = hybridTable.userView
        val result = hybridView.load(key)

        // Then: Should return the full item from DynamoDB
        assertNotNull(result)
        assertEquals("John Doe", result?.name)
        assertEquals("Some user data", result?.data)
        assertNull(result?.s3Key)

        // Verify S3 was not called
        verify(exactly = 0) { s3Client.getObject(any<GetObjectRequest>()) }
    }

    @Test
    fun `test single load - item archived to S3`() {
        // Given: A pointer in DynamoDB and full data in S3
        val key = UserKey("user456", "v2")
        val pointerItem = UserItem(
            userId = "user456",
            version = "v2",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "Jane Smith",
            email = "jane@example.com",
            data = "",  // Data cleared in pointer
            s3Key = "archived/users/user456/v2/2024-01-01.json.gz",
            archivedAt = Instant.now().minus(20, ChronoUnit.DAYS)
        )

        val fullItem = UserItem(
            userId = "user456",
            version = "v2",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "Jane Smith",
            email = "jane@example.com",
            data = "Large amount of user data that was archived",
            s3Key = null,
            archivedAt = null
        )

        // Mock DynamoDB returning pointer
        val mockTable = mockk<UserTable>()
        val mockView = mockk<InlineView<UserKey, UserItem>>()

        every { regularDb.users } returns mockTable
        every { mockTable.userView } returns mockView
        every { mockView.load(key) } returns pointerItem

        // Mock S3 returning full data
        val compressedData = compress(objectMapper.writeValueAsBytes(fullItem))
        val s3Object = mockk<S3Object>()
        val s3Stream = ByteArrayInputStream(compressedData)

        every { s3Client.getObject(any<GetObjectRequest>()) } returns s3Object
        every { s3Object.objectContent } returns s3Stream

        // When: Loading the item through hybrid view
        val hybridTable = hybridDb.users
        val hybridView = hybridTable.userView
        val result = hybridView.load(key)

        // Then: Should return the full item from S3
        assertNotNull(result)
        assertEquals("Jane Smith", result?.name)
        assertEquals("Large amount of user data that was archived", result?.data)

        // Verify S3 was called with correct parameters
        verify(exactly = 1) {
            s3Client.getObject(match<GetObjectRequest> {
                it.bucketName == "test-bucket" &&
                it.key == "archived/users/user456/v2/2024-01-01.json.gz"
            })
        }
    }

    @Test
    fun `test query - mixed items (some archived, some not)`() {
        // Given: Query returns mix of pointers and full items
        val queryCondition = KeyCondition.BeginsWith("user")

        val item1 = UserItem(
            userId = "user001",
            version = "v1",
            createdAt = Instant.now(),
            name = "User One",
            email = "one@example.com",
            data = "Data one",
            s3Key = null  // Not archived
        )

        val pointer2 = UserItem(
            userId = "user002",
            version = "v1",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "User Two",
            email = "two@example.com",
            data = "",  // Cleared
            s3Key = "archived/users/user002/v1/2024-01-01.json.gz",
            archivedAt = Instant.now().minus(20, ChronoUnit.DAYS)
        )

        val fullItem2 = UserItem(
            userId = "user002",
            version = "v1",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "User Two",
            email = "two@example.com",
            data = "Archived data for user two",
            s3Key = null,
            archivedAt = null
        )

        val item3 = UserItem(
            userId = "user003",
            version = "v1",
            createdAt = Instant.now(),
            name = "User Three",
            email = "three@example.com",
            data = "Data three",
            s3Key = null  // Not archived
        )

        // Mock DynamoDB query returning mixed results
        val mockTable = mockk<UserTable>()
        val mockView = mockk<InlineView<UserKey, UserItem>>()
        val mockPage = Page(
            contents = listOf(item1, pointer2, item3),
            offset = null,
            scannedCount = 3,
            consumedCapacity = null
        )

        every { regularDb.users } returns mockTable
        every { mockTable.userView } returns mockView
        every { mockView.query(queryCondition) } returns mockPage

        // Mock S3 for the archived item
        val compressedData = compress(objectMapper.writeValueAsBytes(fullItem2))
        val s3Object = mockk<S3Object>()
        val s3Stream = ByteArrayInputStream(compressedData)

        every {
            s3Client.getObject(match<GetObjectRequest> {
                it.key == "archived/users/user002/v1/2024-01-01.json.gz"
            })
        } returns s3Object
        every { s3Object.objectContent } returns s3Stream

        // When: Querying through hybrid view
        val hybridTable = hybridDb.users
        val hybridView = hybridTable.userView
        val result = hybridView.query(queryCondition)

        // Then: Should return all items with S3 pointers hydrated
        assertEquals(3, result.contents.size)

        val resultItem1 = result.contents[0] as UserItem
        assertEquals("User One", resultItem1.name)
        assertEquals("Data one", resultItem1.data)

        val resultItem2 = result.contents[1] as UserItem
        assertEquals("User Two", resultItem2.name)
        assertEquals("Archived data for user two", resultItem2.data)  // Hydrated from S3

        val resultItem3 = result.contents[2] as UserItem
        assertEquals("User Three", resultItem3.name)
        assertEquals("Data three", resultItem3.data)

        // Verify S3 was called only for the archived item
        verify(exactly = 1) { s3Client.getObject(any<GetObjectRequest>()) }
    }

    @Test
    fun `test scan - handles S3 pointers correctly`() {
        // Given: Scan returns items with some archived
        val pointer1 = UserItem(
            userId = "scan001",
            version = "v1",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "Scan User",
            email = "scan@example.com",
            data = "",
            s3Key = "archived/users/scan001/v1/2024-01-01.json.gz",
            archivedAt = Instant.now().minus(20, ChronoUnit.DAYS)
        )

        val fullItem1 = UserItem(
            userId = "scan001",
            version = "v1",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "Scan User",
            email = "scan@example.com",
            data = "Archived scan data",
            s3Key = null,
            archivedAt = null
        )

        // Mock DynamoDB scan
        val mockTable = mockk<UserTable>()
        val mockView = mockk<InlineView<UserKey, UserItem>>()
        val mockPage = Page(
            contents = listOf(pointer1),
            offset = null,
            scannedCount = 1,
            consumedCapacity = null
        )

        every { regularDb.users } returns mockTable
        every { mockTable.userView } returns mockView
        every { mockView.scan(any()) } returns mockPage

        // Mock S3
        val compressedData = compress(objectMapper.writeValueAsBytes(fullItem1))
        val s3Object = mockk<S3Object>()
        val s3Stream = ByteArrayInputStream(compressedData)

        every { s3Client.getObject(any<GetObjectRequest>()) } returns s3Object
        every { s3Object.objectContent } returns s3Stream

        // When: Scanning through hybrid view
        val hybridTable = hybridDb.users
        val hybridView = hybridTable.userView
        val result = hybridView.scan()

        // Then: Should hydrate S3 pointers
        assertEquals(1, result.contents.size)
        val resultItem = result.contents[0] as UserItem
        assertEquals("Scan User", resultItem.name)
        assertEquals("Archived scan data", resultItem.data)  // Hydrated from S3

        verify(exactly = 1) { s3Client.getObject(any<GetObjectRequest>()) }
    }

    @Test
    fun `test S3 configuration is properly used`() {
        // Given: Custom S3 configuration
        val customConfig = HybridConfig(
            s3Config = HybridConfig.S3Config(
                bucketName = "custom-bucket",
                keyPrefix = "custom-prefix"
            ),
            archivalConfig = HybridConfig.ArchivalConfig(enabled = true)
        )

        val customHybridDb = HybridLogicalDb.create(
            regularDb,
            s3Client,
            customConfig,
            objectMapper
        )

        // Setup mock for pointer item
        val key = UserKey("config-test", "v1")
        val pointerItem = UserItem(
            userId = "config-test",
            version = "v1",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "Config Test",
            email = "config@example.com",
            data = "",
            s3Key = "custom-prefix/users/config-test/v1/2024-01-01.json.gz",
            archivedAt = Instant.now()
        )

        val mockTable = mockk<UserTable>()
        val mockView = mockk<InlineView<UserKey, UserItem>>()

        every { regularDb.users } returns mockTable
        every { mockTable.userView } returns mockView
        every { mockView.load(key) } returns pointerItem

        // Mock S3
        val s3Object = mockk<S3Object>()
        val dummyData = compress(objectMapper.writeValueAsBytes(pointerItem))
        every { s3Client.getObject(any<GetObjectRequest>()) } returns s3Object
        every { s3Object.objectContent } returns ByteArrayInputStream(dummyData)

        // When: Loading through custom configured hybrid DB
        val hybridTable = customHybridDb.users
        val hybridView = hybridTable.userView
        hybridView.load(key)

        // Then: Should use custom bucket name
        verify(exactly = 1) {
            s3Client.getObject(match<GetObjectRequest> {
                it.bucketName == "custom-bucket" &&
                it.key == "custom-prefix/users/config-test/v1/2024-01-01.json.gz"
            })
        }
    }

    @Test
    fun `test graceful fallback when S3 read fails`() {
        // Given: S3 read fails
        val key = UserKey("fail-test", "v1")
        val pointerItem = UserItem(
            userId = "fail-test",
            version = "v1",
            createdAt = Instant.now().minus(200, ChronoUnit.DAYS),
            name = "Fail Test",
            email = "fail@example.com",
            data = "",  // No data in pointer
            s3Key = "archived/users/fail-test/v1/2024-01-01.json.gz",
            archivedAt = Instant.now()
        )

        val mockTable = mockk<UserTable>()
        val mockView = mockk<InlineView<UserKey, UserItem>>()

        every { regularDb.users } returns mockTable
        every { mockTable.userView } returns mockView
        every { mockView.load(key) } returns pointerItem

        // S3 throws exception
        every { s3Client.getObject(any<GetObjectRequest>()) } throws
            RuntimeException("S3 service unavailable")

        // When: Loading the item
        val hybridTable = hybridDb.users
        val hybridView = hybridTable.userView
        val result = hybridView.load(key)

        // Then: Should return the pointer as fallback
        assertNotNull(result)
        assertEquals("Fail Test", result?.name)
        assertEquals("", result?.data)  // Empty data from pointer
        assertEquals("archived/users/fail-test/v1/2024-01-01.json.gz", result?.s3Key)
    }

    // Helper function to compress data
    private fun compress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }
}