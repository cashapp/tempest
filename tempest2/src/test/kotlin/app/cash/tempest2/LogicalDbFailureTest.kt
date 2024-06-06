/*
 * Copyright 2021 Square Inc.
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

package app.cash.tempest2

import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.testDb
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest
import software.amazon.awssdk.services.dynamodb.model.WriteRequest

class LogicalDbFailureTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  // Fake client that prevents batch deletes from succeeding
  class FakeDynamoDbEnhancedClient(private val realClient: DynamoDbEnhancedClient) :
    DynamoDbEnhancedClient by realClient {
    override fun batchWriteItem(request: BatchWriteItemEnhancedRequest?): BatchWriteResult {
      val unprocessedDeletes = request!!.writeBatches().map { writeBatch ->
        writeBatch.tableName() to writeBatch.writeRequests().map { writeRequest ->
          val key = writeRequest.deleteRequest().key()
          WriteRequest.builder()
            .deleteRequest(
              DeleteRequest.builder()
                .key(key)
                .build()
            )
            .build()
        }
      }

      val results = unprocessedDeletes.associate { (tableName, keys) ->
        tableName to keys
      }
      return BatchWriteResult.builder()
        .unprocessedRequests(results)
        .build()
    }
  }

  private val musicDb by lazy {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(db.dynamoDb)
      .extensions(listOf())
      .build()
    val fakeEnhancedClient = FakeDynamoDbEnhancedClient(enhancedClient)
    LogicalDb.create(MusicDb::class, fakeEnhancedClient)
  }
  private val musicTable by lazy { musicDb.music }

  @Test
  fun `batch write handles unprocessed deletes accurately`() {
    val albumTracks = listOf(
      AlbumTrack("ALBUM_1", 1, "dreamin'", Duration.parse("PT3M28S")),
      AlbumTrack("ALBUM_1", 2, "what you do to me", Duration.parse("PT3M24S")),
      AlbumTrack("ALBUM_1", 3, "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicTable.albumTracks.save(albumTrack)
    }

    val batchWriteResult = musicDb.batchWrite(
      BatchWriteSet.Builder()
        .delete(albumTracks.map { it.key })
        .build()
    )

    assertThat(batchWriteResult.unprocessedDeletes.map { it.partitionKeyValue().s() to it.sortKeyValue().get().s() })
      .containsExactlyInAnyOrderElementsOf(
        albumTracks.map {
          it.album_token to "TRACK_${it.track_token}"
        }
      )
  }
}
