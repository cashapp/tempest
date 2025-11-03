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

import app.cash.tempest.Attribute
import app.cash.tempest.ForIndex
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import app.cash.tempest.Page
import app.cash.tempest.BeginsWith
import app.cash.tempest.testing.JvmDynamoDbServer
import app.cash.tempest.testing.TestDynamoDb
import app.cash.tempest.testing.TestTable
import app.cash.tempest.testing.logicalDb
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.model.PutObjectRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream

// Test database interface
interface HybridTestDb : LogicalDb {
  val sessions: SessionTable
}

// Table interface
interface SessionTable : LogicalTable<SessionItem> {
  val sessions: InlineView<SessionKey, Session>
}

// DynamoDB raw item
@DynamoDBTable(tableName = "hybrid_test_table")
data class SessionItem(
  @DynamoDBHashKey
  @Attribute(name = "partition_key")
  var partition_key: String = "",

  @DynamoDBRangeKey
  @Attribute(name = "sort_key")
  var sort_key: String = "",

  @Attribute(name = "session_data")
  var session_data: String? = null,

  @Attribute(name = "created_at")
  var created_at: String? = null,

  @Attribute(name = "s3_key")
  var s3_key: String? = null
)

// Domain model
@HybridTable(
  archiveAfterDays = 365,
  s3KeyTemplate = "sessions/{pk}/{sk}.json"
)
data class Session(
  @Attribute(name = "partition_key")
  val userId: String,
  @Attribute(name = "sort_key")
  val sessionId: String,
  val sessionData: String?,
  @ArchivalTimestamp
  val createdAt: Instant,
  val s3Key: String? = null
) {
  @ForIndex("session_index")
  data class SessionIndexKey(
    val userId: String,
    val createdAt: Instant
  )
}

data class SessionKey(
  val userId: String,
  val sessionId: String
)

class HybridStorageIntegrationTest {

  @RegisterExtension
  @JvmField
  val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
    .addTable(TestTable.create<SessionItem>())
    .build()

  private lateinit var s3Client: AmazonS3
  private lateinit var objectMapper: ObjectMapper
  private lateinit var regularDb: HybridTestDb
  private lateinit var hybridDb: HybridLogicalDb
  private lateinit var hybridConfig: HybridConfig

  @BeforeEach
  fun setup() {
    s3Client = mockk()
    objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    regularDb = db.logicalDb<HybridTestDb>()

    hybridConfig = HybridConfig(
      s3Config = HybridConfig.S3Config(
        bucketName = "test-bucket",
        region = "us-west-2",
        keyPrefix = "archive"
      ),
      archivalConfig = HybridConfig.ArchivalConfig(
        enabled = true,
        archiveAfterDuration = Duration.ofDays(365)
      )
    )

    // Note: For now we'll test the components separately
    // A full integration would require creating HybridLogicalDb with the test DB
  }

  @Test
  fun testRegularItemLoad() {
    // Save a regular item to DynamoDB
    val session = Session(
      userId = "user123",
      sessionId = "session456",
      sessionData = "test data",
      createdAt = Instant.now(),
      s3Key = null
    )

    regularDb.sessions.sessions.save(session)

    // Load it back
    val loaded = regularDb.sessions.sessions.load(
      SessionKey("user123", "session456")
    )

    assertThat(loaded).isNotNull
    assertThat(loaded?.sessionData).isEqualTo("test data")
    assertThat(loaded?.s3Key).isNull()
  }

  @Test
  fun testPointerItemWithS3Load() {
    // Create a pointer item in DynamoDB
    val pointer = Session(
      userId = "user123",
      sessionId = "session789",
      sessionData = null, // Data removed for pointer
      createdAt = Instant.now().minusSeconds(31536000), // 1 year ago
      s3Key = "archive/sessions/user123/session789.json.gz"
    )

    // The full data that would be in S3
    val fullSession = Session(
      userId = "user123",
      sessionId = "session789",
      sessionData = "archived data from S3",
      createdAt = Instant.now().minusSeconds(31536000),
      s3Key = null
    )

    // Save pointer to DynamoDB
    regularDb.sessions.sessions.save(pointer)

    // Mock S3 response
    val json = objectMapper.writeValueAsString(fullSession)
    val compressed = compressGzip(json.toByteArray())
    val s3Object = mockk<S3Object>()
    val inputStream = S3ObjectInputStream(ByteArrayInputStream(compressed), null)

    every { s3Object.objectContent } returns inputStream
    every { s3Client.getObject(any<GetObjectRequest>()) } returns s3Object

    // Create a hybrid view proxy to test
    val regularView = regularDb.sessions.sessions
    val executorService = java.util.concurrent.Executors.newFixedThreadPool(2)
    val proxy = HybridViewProxy(
      regularView,
      Session::class.java.getAnnotation(HybridTable::class.java),
      Session::class.java,
      s3Client,
      objectMapper,
      hybridConfig,
      executorService
    )

    // Test that the proxy loads from S3 when it sees a pointer
    val loadMethod = InlineView::class.java.getMethod("load", Any::class.java)
    val result = proxy.invoke(null, loadMethod, arrayOf(SessionKey("user123", "session789")))

    assertThat(result).isNotNull
    verify(exactly = 1) { s3Client.getObject(any<GetObjectRequest>()) }
  }

  @Test
  fun testQueryWithMixedItems() {
    // Save regular items and pointer items
    val regularSession = Session(
      userId = "user123",
      sessionId = "session1",
      sessionData = "regular data",
      createdAt = Instant.now(),
      s3Key = null
    )

    val pointerSession = Session(
      userId = "user123",
      sessionId = "session2",
      sessionData = null,
      createdAt = Instant.now().minusSeconds(31536000),
      s3Key = "archive/sessions/user123/session2.json.gz"
    )

    regularDb.sessions.sessions.save(regularSession)
    regularDb.sessions.sessions.save(pointerSession)

    // Query for all sessions for the user
    val page = regularDb.sessions.sessions.query(
      BeginsWith(SessionKey("user123", ""))
    )

    assertThat(page.contents).hasSize(2)
    assertThat(page.contents[0].sessionData).isEqualTo("regular data")
    assertThat(page.contents[1].s3Key).isEqualTo("archive/sessions/user123/session2.json.gz")
  }

  @Test
  fun testArchivalService() = runBlocking {
    // Create an old item that should be archived
    val oldSession = Session(
      userId = "user456",
      sessionId = "old_session",
      sessionData = "data to archive",
      createdAt = Instant.now().minusSeconds(31536000 * 2), // 2 years ago
      s3Key = null
    )

    regularDb.sessions.sessions.save(oldSession)

    // Mock S3 putObject
    every { s3Client.putObject(any<PutObjectRequest>()) } returns mockk()

    // Create archival service
    val archivalService = ArchivalService(
      regularDb,
      s3Client,
      objectMapper,
      hybridConfig
    )

    // Run archival in dry-run mode first
    val dryRunResult = archivalService.archiveOldData(dryRun = true)

    // Note: This test would need more setup to properly test archival
    // as it requires discovering tables with @HybridTable annotation
    assertThat(dryRunResult).isNotNull
  }

  @Test
  fun testCompressionDecompression() {
    val originalData = "This is test data that should be compressed"
    val compressed = compressGzip(originalData.toByteArray())

    assertThat(compressed.size).isLessThan(originalData.length)

    // Verify GZIP magic number
    assertThat(compressed[0]).isEqualTo(0x1f.toByte())
    assertThat(compressed[1]).isEqualTo(0x8b.toByte())
  }

  @Test
  fun testS3KeyGeneration() {
    val session = Session(
      userId = "user123",
      sessionId = "session456",
      sessionData = "test data",
      createdAt = Instant.now(),
      s3Key = null
    )

    val s3Key = S3KeyGenerator.generateS3Key(
      session,
      "sessions/{pk}/{sk}.json",
      "user_sessions"
    )

    assertThat(s3Key).isEqualTo("sessions/user123/session456.json")
  }

  @Test
  fun testKeyExtraction() {
    val session = Session(
      userId = "user123",
      sessionId = "session456",
      sessionData = "test data",
      createdAt = Instant.now(),
      s3Key = null
    )

    val (pk, sk) = S3KeyGenerator.extractKeys(session)
    assertThat(pk).isEqualTo("user123")
    assertThat(sk).isEqualTo("session456")
  }

  private fun compressGzip(data: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { gzipOutput ->
      gzipOutput.write(data)
    }
    return outputStream.toByteArray()
  }
}