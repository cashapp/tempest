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
import app.cash.tempest.musiclibrary.MusicTable
import app.cash.tempest.musiclibrary.PlaylistInfo
import app.cash.tempest.musiclibrary.THE_WALL
import app.cash.tempest.musiclibrary.givenAlbums
import app.cash.tempest.musiclibrary.testDb
import app.cash.tempest.testing.logicalDb
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class WritingPagerTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicDb by lazy { db.logicalDb<MusicDb>() }
  private val musicTable by lazy { musicDb.music }

  @Test
  fun write() {
    musicTable.givenAlbums(THE_WALL)
    val tracks = (1..25L).map { AlbumTrack.Key(THE_WALL.album_token, it) }
    val playlistV1 = PlaylistInfo(
      "PLAYLIST_1",
      "Pink Floyd Anthology",
      emptyList()
    )
    musicTable.playlistInfo.save(playlistV1)

    musicDb.transactionWritingPager(
      tracks,
      maxTransactionItems = 10,
      handler = AlbumTrackWritingPagerHandler(playlistV1.playlist_token, musicTable)
    ).execute()

    val playlistInfo = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistV1.playlist_token))!!
    assertThat(playlistInfo.playlist_tracks).containsExactlyElementsOf(tracks)
    assertThat(playlistInfo.playlist_version).isEqualTo(4)
  }

  class AlbumTrackWritingPagerHandler(
    private val playlistToken: String,
    private val musicTable: MusicTable
  ) : WritingPager.Handler<AlbumTrack.Key> {
    private lateinit var currentPagePlaylistInfo: PlaylistInfo
    private lateinit var currentPageTracks: List<AlbumTrack.Key>

    override fun eachPage(proceed: () -> Unit) {
      proceed()
    }

    override fun beforePage(
      remainingUpdates: List<AlbumTrack.Key>,
      maxTransactionItems: Int
    ): Int {
      // Reserve 1 for the playlist info at the end.
      currentPageTracks = remainingUpdates.take((maxTransactionItems - 1))
      currentPagePlaylistInfo = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistToken))!!
      return currentPageTracks.size
    }

    override fun item(builder: TransactionWriteSet.Builder, item: AlbumTrack.Key) {
      builder.checkCondition(item, trackExists())
    }

    override fun finishPage(builder: TransactionWriteSet.Builder) {
      val playlistInfo = currentPagePlaylistInfo
      builder.save(
        playlistInfo.copy(
          playlist_tracks = playlistInfo.playlist_tracks + currentPageTracks,
          playlist_version = playlistInfo.playlist_version + 1
        ),
        ifPlaylistVersionIs(playlistInfo.playlist_version)
      )
    }
  }
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
