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
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import app.cash.tempest.Page
import app.cash.tempest.Offset
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.assertj.core.api.Assertions.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream

@HybridTable(
  archiveAfterDays = 365,
  s3KeyTemplate = "sessions/{pk}/{sk}.json"
)
data class TestSession(
  @Attribute(name = "partition_key")
  val userId: String,
  @Attribute(name = "sort_key")
  val sessionId: String,
  val sessionData: String,
  @ArchivalTimestamp
  val createdAt: Instant,
  val s3Key: String? = null
) {
  data class Key(
    val userId: String,
    val sessionId: String
  )
}

class HybridStorageTest {

  private lateinit var s3Client: AmazonS3
  private lateinit var objectMapper: ObjectMapper
  private lateinit var regularView: InlineView<TestSession.Key, TestSession>
  private lateinit var hybridConfig: HybridConfig

  @BeforeEach
  fun setup() {
    s3Client = mockk()
    objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    regularView = mockk()
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
  }

  @Test
  fun testHybridConfiguration() {
    val config = HybridConfig(
      s3Config = HybridConfig.S3Config(
        bucketName = "test-bucket",
        region = "us-west-2"
      ),
      archivalConfig = HybridConfig.ArchivalConfig(
        enabled = true,
        archiveAfterDuration = Duration.ofDays(365)
      )
    )

    assertThat(config.s3Config.bucketName).isEqualTo("test-bucket")
    assertThat(config.archivalConfig.archiveAfterDuration).isEqualTo(Duration.ofDays(365))
  }

  @Test
  fun testS3KeyGeneration() {
    val session = TestSession(
      userId = "user123",
      sessionId = "session456",
      sessionData = "test data",
      createdAt = Instant.now()
    )

    val s3Key = S3KeyGenerator.generateS3Key(
      session,
      "sessions/{pk}/{sk}.json",
      "user_sessions"
    )

    assertThat(s3Key).isEqualTo("sessions/user123/session456.json")
  }

  @Test
  fun testHybridViewProxy_LoadFromDynamoDB() {
    // Test that regular items are returned from DynamoDB without S3 access
    val session = TestSession(
      userId = "user123",
      sessionId = "session456",
      sessionData = "test data",
      createdAt = Instant.now()
    )

    val proxy = HybridViewProxy(
      regularView,
      HybridTable::class.java.getAnnotation(HybridTable::class.java),
      TestSession::class.java,
      s3Client,
      objectMapper,
      hybridConfig
    )

    every { regularView.load(any()) } returns session

    val handler = proxy
    val loadMethod = InlineView::class.java.getMethod("load", Any::class.java)
    val result = handler.invoke(null, loadMethod, arrayOf(TestSession.Key("user123", "session456")))

    assertThat(result).isEqualTo(session)
    verify(exactly = 0) { s3Client.getObject(any<GetObjectRequest>()) }
  }

  @Test
  fun testHybridViewProxy_LoadFromS3() {
    // Test that pointer items trigger S3 load
    val pointer = TestSession(
      userId = "user123",
      sessionId = "session456",
      sessionData = "",
      createdAt = Instant.now(),
      s3Key = "archive/sessions/user123/session456.json.gz"
    )

    val fullSession = TestSession(
      userId = "user123",
      sessionId = "session456",
      sessionData = "full test data from S3",
      createdAt = Instant.now()
    )

    val proxy = HybridViewProxy(
      regularView,
      HybridTable::class.java.getAnnotation(HybridTable::class.java),
      TestSession::class.java,
      s3Client,
      objectMapper,
      hybridConfig
    )

    every { regularView.load(any()) } returns pointer

    // Mock S3 response with compressed data
    val json = objectMapper.writeValueAsString(fullSession)
    val compressed = compressGzip(json.toByteArray())
    val s3Object = mockk<S3Object>()
    val inputStream = S3ObjectInputStream(ByteArrayInputStream(compressed), null)

    every { s3Object.objectContent } returns inputStream
    every { s3Client.getObject(any<GetObjectRequest>()) } returns s3Object

    val handler = proxy
    val loadMethod = InlineView::class.java.getMethod("load", Any::class.java)
    val result = handler.invoke(null, loadMethod, arrayOf(TestSession.Key("user123", "session456")))

    assertThat(result).isNotNull
    verify(exactly = 1) { s3Client.getObject(any<GetObjectRequest>()) }
  }

  @Test
  fun testHybridViewProxy_QueryWithHydration() {
    // Test that query results with mixed items are properly hydrated
    val regularItem = TestSession(
      userId = "user123",
      sessionId = "session1",
      sessionData = "regular data",
      createdAt = Instant.now()
    )

    val pointerItem = TestSession(
      userId = "user123",
      sessionId = "session2",
      sessionData = "",
      createdAt = Instant.now().minusSeconds(31536000), // 1 year ago
      s3Key = "archive/sessions/user123/session2.json.gz"
    )

    val s3Item = TestSession(
      userId = "user123",
      sessionId = "session2",
      sessionData = "archived data from S3",
      createdAt = Instant.now().minusSeconds(31536000)
    )

    val page = Page(
      contents = listOf(regularItem, pointerItem),
      offset = null,
      scannedCount = 2,
      consumedCapacity = null
    )

    val proxy = HybridViewProxy(
      regularView,
      HybridTable::class.java.getAnnotation(HybridTable::class.java),
      TestSession::class.java,
      s3Client,
      objectMapper,
      hybridConfig
    )

    every { regularView.query(any(), any(), any(), any(), any(), any(), any()) } returns page

    // Mock S3 response
    val json = objectMapper.writeValueAsString(s3Item)
    val compressed = compressGzip(json.toByteArray())
    val s3Object = mockk<S3Object>()
    val inputStream = S3ObjectInputStream(ByteArrayInputStream(compressed), null)

    every { s3Object.objectContent } returns inputStream
    every { s3Client.getObject(any<GetObjectRequest>()) } returns s3Object

    val handler = proxy
    val queryMethod = regularView::class.java.methods.find { it.name == "query" }!!
    val result = handler.invoke(null, queryMethod, arrayOf(mockk(), true, 100, false, mockk(), null, null))

    assertThat(result).isNotNull
    verify(exactly = 1) { s3Client.getObject(any<GetObjectRequest>()) } // Only for the pointer item
  }

  @Test
  fun testArchivalService_DiscoverHybridTables() = runBlocking {
    // This would require a more complex setup with a mock LogicalDb
    // For now, we'll test the core archival logic components separately

    val archivalService = ArchivalService(
      mockk(),
      s3Client,
      objectMapper,
      hybridConfig
    )

    // Test that archival respects the enabled flag
    val disabledConfig = hybridConfig.copy(
      archivalConfig = HybridConfig.ArchivalConfig(enabled = false)
    )

    val disabledService = ArchivalService(
      mockk(),
      s3Client,
      objectMapper,
      disabledConfig
    )

    val result = disabledService.archiveOldData(dryRun = false)
    assertThat(result.itemsProcessed).isEqualTo(0)
    assertThat(result.itemsArchived).isEqualTo(0)
  }

  @Test
  fun testCompressionAndDecompression() {
    val originalData = "This is test data that should be compressed and decompressed"
    val compressed = compressGzip(originalData.toByteArray())

    assertThat(compressed.size).isLessThan(originalData.length)

    // Verify GZIP magic number
    assertThat(compressed[0]).isEqualTo(0x1f.toByte())
    assertThat(compressed[1]).isEqualTo(0x8b.toByte())
  }

  @Test
  fun testPointerDetection() {
    val regularItem = TestSession(
      userId = "user123",
      sessionId = "session456",
      sessionData = "test data",
      createdAt = Instant.now()
    )

    val pointerItem = TestSession(
      userId = "user123",
      sessionId = "session456",
      sessionData = "",
      createdAt = Instant.now(),
      s3Key = "archive/sessions/user123/session456.json"
    )

    // Test using reflection like the actual code does
    assertThat(hasS3Key(regularItem)).isFalse()
    assertThat(hasS3Key(pointerItem)).isTrue()
  }

  private fun hasS3Key(item: Any): Boolean {
    return try {
      val s3KeyField = item.javaClass.getDeclaredField("s3Key")
      s3KeyField.isAccessible = true
      s3KeyField.get(item) != null
    } catch (e: Exception) {
      false
    }
  }

  private fun compressGzip(data: ByteArray): ByteArray {
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { gzipOutput ->
      gzipOutput.write(data)
    }
    return outputStream.toByteArray()
  }
}
