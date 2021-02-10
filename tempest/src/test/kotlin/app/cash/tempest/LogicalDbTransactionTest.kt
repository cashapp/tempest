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

package app.cash.tempest

import app.cash.tempest.musiclibrary.AlbumTrack
import app.cash.tempest.musiclibrary.MusicDb
import app.cash.tempest.musiclibrary.MusicDbTestModule
import app.cash.tempest.musiclibrary.PlaylistInfo
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import javax.inject.Inject

@MiskTest(startService = true)
class LogicalDbTransactionTest {

  @MiskTestModule
  val module = MusicDbTestModule()

  @Inject lateinit var musicDb: MusicDb

  private val musicTable get() = musicDb.music

  @Test
  fun transactionLoad() {
    val albumTracks = listOf(
      AlbumTrack(
        "ALBUM_1",
        1,
        "dreamin'",
        Duration.parse("PT3M28S")
      ),
      AlbumTrack(
        "ALBUM_1",
        2,
        "what you do to me",
        Duration.parse("PT3M24S")
      ),
      AlbumTrack(
        "ALBUM_1",
        3,
        "too slow",
        Duration.parse("PT2M27S")
      )
    )
    for (albumTrack in albumTracks) {
      musicTable.albumTracks.save(albumTrack)
    }
    val playlistInfo = PlaylistInfo(
      "PLAYLIST_1",
      "WFH Music",
      listOf(AlbumTrack.Key("ALBUM_1", 1))
    )
    musicTable.playlistInfo.save(playlistInfo)

    val loadedItems = musicDb.transactionLoad(
      PlaylistInfo.Key("PLAYLIST_1"),
      AlbumTrack.Key("ALBUM_1", 1),
      AlbumTrack.Key("ALBUM_1", 2),
      AlbumTrack.Key("ALBUM_1", 3)
    )
    assertThat(loadedItems.getItems<AlbumTrack>()).containsAll(albumTracks)
    assertThat(loadedItems.getItems<PlaylistInfo>()).contains(playlistInfo)
  }

  @Test
  fun transactionLoadAfterTransactionWrite() {
    val albumTracks = listOf(
      AlbumTrack(
        "ALBUM_1",
        1,
        "dreamin'",
        Duration.parse("PT3M28S")
      ),
      AlbumTrack(
        "ALBUM_1",
        2,
        "what you do to me",
        Duration.parse("PT3M24S")
      ),
      AlbumTrack(
        "ALBUM_1",
        3,
        "too slow",
        Duration.parse("PT2M27S")
      )
    )
    val playlistInfo =
      PlaylistInfo("PLAYLIST_1", "WFH Music", listOf())

    val writeTransaction = TransactionWriteSet.Builder()
      .save(albumTracks[0])
      .save(albumTracks[1])
      .save(albumTracks[2])
      .save(playlistInfo)
      .build()
    musicDb.transactionWrite(writeTransaction)

    // Read items at the same time in a serializable manner.
    val loadedItems = musicDb.transactionLoad(
      PlaylistInfo.Key("PLAYLIST_1"),
      AlbumTrack.Key("ALBUM_1", 1),
      AlbumTrack.Key("ALBUM_1", 2),
      AlbumTrack.Key("ALBUM_1", 3)
    )
    assertThat(loadedItems.getItems<AlbumTrack>()).containsAll(albumTracks)
    assertThat(loadedItems.getItems<PlaylistInfo>()).containsExactly(playlistInfo)
  }

  @Test
  fun conditionalUpdateInTransactionWrite() {
    val playlistInfoV1 =
      PlaylistInfo("PLAYLIST_1", "WFH Music", emptyList())
    musicTable.playlistInfo.save(playlistInfoV1)
    val albumTrack = AlbumTrack(
      "ALBUM_1",
      1,
      "dreamin'",
      Duration.parse("PT3M28S")
    )
    musicTable.albumTracks.save(albumTrack)

    // Add a PlaylistEntry and update PlaylistInfo, in an ACID manner using transactionWrite.
    val playlistInfoV2 = playlistInfoV1.copy(
      playlist_name = "WFH Forever Music",
      playlist_version = playlistInfoV1.playlist_version + 1
    )
    val writeTransaction = TransactionWriteSet.Builder()
      .save(
        playlistInfoV2,
        ifPlaylistVersionIs(playlistInfoV1.playlist_version)
      )
      .delete(AlbumTrack.Key("ALBUM_1", 1))
      .build()
    musicDb.transactionWrite(writeTransaction)

    val loadedItems = musicDb.transactionLoad(
      PlaylistInfo.Key("PLAYLIST_1"),
      AlbumTrack.Key("ALBUM_1", 1)
    )
    assertThat(loadedItems.getItems<PlaylistInfo>()).containsExactly(playlistInfoV2)
    assertThat(loadedItems.getItems<AlbumTrack>()).isEmpty()
  }

  @Test
  fun conditionalUpdateFailureInTransactionWrite() {
    val playlistInfoV1 =
      PlaylistInfo("PLAYLIST_1", "WFH Music", emptyList())
    musicTable.playlistInfo.save(playlistInfoV1)
    val albumTrack = AlbumTrack(
      "ALBUM_1",
      1,
      "dreamin'",
      Duration.parse("PT3M28S")
    )
    musicTable.albumTracks.save(albumTrack)

    // Add a PlaylistEntry and update PlaylistInfo, in an ACID manner using transactionWrite.
    val playlistInfoV2 = playlistInfoV1.copy(
      playlist_version = playlistInfoV1.playlist_version + 1
    )

    val writeTransaction = TransactionWriteSet.Builder()
      .save(
        playlistInfoV2,
        ifPlaylistVersionIs(playlistInfoV1.playlist_version)
      )
      .delete(AlbumTrack.Key("ALBUM_1", 1))
      .build()
    // Introduce a race condition.
    musicTable.playlistInfo.save(playlistInfoV2)

    val exception = assertThrows<AmazonServiceException> {
      musicDb.transactionWrite(writeTransaction)
    }
    assertThat(exception.errorCode).isEqualTo(TransactionCanceledException::class.simpleName)
  }

  @Test
  fun conditionCheckInTransactionWrite() {
    val playlistInfoV1 =
      PlaylistInfo("PLAYLIST_1", "WFH Music", emptyList())
    musicTable.playlistInfo.save(playlistInfoV1)
    val albumTrack = AlbumTrack(
      "ALBUM_1",
      1,
      "dreamin'",
      Duration.parse("PT3M28S")
    )
    musicTable.albumTracks.save(albumTrack)

    val playlistInfoV2 = playlistInfoV1.copy(
      playlist_tracks = playlistInfoV1.playlist_tracks + AlbumTrack.Key("ALBUM_1", 1),
      playlist_version = playlistInfoV1.playlist_version + 1
    )
    val writeTransaction = TransactionWriteSet.Builder()
      .save(
        playlistInfoV2,
        ifPlaylistVersionIs(playlistInfoV1.playlist_version)
      )
      // Add a PlaylistEntry only if the AlbumTrack exists.
      .checkCondition(
        AlbumTrack.Key("ALBUM_1", 1),
        trackExists()
      )
      .build()
    musicDb.transactionWrite(writeTransaction)

    val loadedItems = musicDb.transactionLoad(PlaylistInfo.Key("PLAYLIST_1"))
    assertThat(loadedItems.getItems<PlaylistInfo>()).containsExactly(playlistInfoV2)
  }

  @Test
  fun conditionCheckFailureInTransactionWrite() {
    val playlistInfoV1 =
      PlaylistInfo("PLAYLIST_1", "WFH Music", emptyList())
    musicTable.playlistInfo.save(playlistInfoV1)

    val playlistInfoV2 = playlistInfoV1.copy(
      playlist_tracks = playlistInfoV1.playlist_tracks + AlbumTrack.Key("ALBUM_1", 1),
      playlist_version = playlistInfoV1.playlist_version + 1
    )
    val writeTransaction = TransactionWriteSet.Builder()
      .save(
        playlistInfoV2,
        ifPlaylistVersionIs(playlistInfoV1.playlist_version)
      )
      // Add a playlist entry only if the AlbumTrack exists.
      .checkCondition(
        AlbumTrack.Key("ALBUM_1", 1),
        trackExists()
      )
      .build()

    val exception = assertThrows<AmazonServiceException> {
      musicDb.transactionWrite(writeTransaction)
    }
    assertThat(exception.errorCode).isEqualTo(TransactionCanceledException::class.simpleName)
  }

  private fun ifPlaylistVersionIs(playlist_version: Long): DynamoDBTransactionWriteExpression {
    return DynamoDBTransactionWriteExpression()
      .withConditionExpression("playlist_version = :playlist_version")
      .withExpressionAttributeValues(
        mapOf(
          ":playlist_version" to AttributeValue().withN("$playlist_version")
        )
      )
  }

  private fun trackExists(): DynamoDBTransactionWriteExpression {
    return DynamoDBTransactionWriteExpression()
      .withConditionExpression("attribute_exists(track_title)")
  }
}
