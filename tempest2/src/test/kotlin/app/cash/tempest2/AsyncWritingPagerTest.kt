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

import app.cash.tempest.musiclibrary.THE_WALL
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.AsyncMusicDb
import app.cash.tempest2.musiclibrary.PlaylistInfo
import app.cash.tempest2.musiclibrary.givenAlbums
import app.cash.tempest2.musiclibrary.testDb
import app.cash.tempest2.testing.asyncLogicalDb
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException

class AsyncWritingPagerTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicDb by lazy { db.asyncLogicalDb<AsyncMusicDb>() }
  private val musicTable by lazy { musicDb.music }

  @Test
  fun write() = runBlockingTest {
    musicTable.givenAlbums(THE_WALL)
    val tracks = (1..25L).map { AlbumTrack.Key(THE_WALL.album_token, it) }
    val playlistV1 = PlaylistInfo(
      "PLAYLIST_1",
      "Pink Floyd Anthology",
      emptyList()
    )
    musicTable.playlistInfo.save(playlistV1)

    val handler = AsyncAlbumTrackWritingPagerHandler(playlistV1.playlist_token, musicTable)

    musicDb.transactionWritingPager(
      tracks,
      maxTransactionItems = 10,
      handler = handler
    ).execute()

    val playlistInfo = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistV1.playlist_token))!!
    assertThat(playlistInfo.playlist_tracks).containsExactlyElementsOf(tracks)
    assertThat(playlistInfo.playlist_version).isEqualTo(4)

    // We wrote 3 pages of up to 9 items each (1 of the 10 max is reserved for the playlist info)
    assertThat(handler.eachPageCounter).isEqualTo(3)
    assertThat(handler.itemCounterMap).isEqualTo(mapOf(0 to 9, 1 to 9, 2 to 7))
    assertThat(handler.finishPageCounter).isEqualTo(3)
    assertThat(handler.pageWrittenCounter).isEqualTo(3)
    assertThat(handler.written).hasSize(3)
  }

  @Test
  fun writeFails() = runBlockingTest {
    musicTable.givenAlbums(THE_WALL)
    val tracks = (1..25L).map { AlbumTrack.Key(THE_WALL.album_token, it) }
    val playlistV1 = PlaylistInfo(
      "PLAYLIST_1",
      "Pink Floyd Anthology",
      emptyList()
    )
    musicTable.playlistInfo.save(playlistV1)

    val handler = AsyncAlbumTrackWritingPagerHandler(
      playlistToken = playlistV1.playlist_token,
      musicTable = musicTable,
      currentVersionOffset = -1)

    assertThatThrownBy {
      runBlockingTest {
        musicDb.transactionWritingPager(
          tracks,
          maxTransactionItems = 10,
          handler = handler
        ).execute()
      }
    }.hasMessageContaining("Write transaction failed")
      .isInstanceOf(TransactionCanceledException::class.java)

    val playlistInfo = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistV1.playlist_token))!!
    assertThat(playlistInfo.playlist_tracks).isEmpty()
    assertThat(playlistInfo.playlist_version).isEqualTo(1)

    // We computed the first page of 10 items and then failed to write.
    assertThat(handler.eachPageCounter).isEqualTo(1)
    assertThat(handler.itemCounterMap).isEqualTo(mapOf(0 to 9))
    assertThat(handler.finishPageCounter).isEqualTo(1)
    assertThat(handler.pageWrittenCounter).isEqualTo(0)
    assertThat(handler.written).isEmpty()
  }

  @Test
  fun extensionFunctionUsage() = runBlockingTest {
    musicTable.givenAlbums(THE_WALL)
    val tracks = (1..5L).map { AlbumTrack.Key(THE_WALL.album_token, it) }
    val playlistV1 = PlaylistInfo(
      "PLAYLIST_1",
      "Pink Floyd Anthology",
      emptyList()
    )
    musicTable.playlistInfo.save(playlistV1)

    val handler = AsyncAlbumTrackWritingPagerHandler(playlistV1.playlist_token, musicTable)

    // Test the extension function directly
    val pager = musicDb.transactionWritingPager(
      tracks,
      maxTransactionItems = 10,
      handler = handler
    )
    
    assertThat(pager).isInstanceOf(AsyncWritingPager::class.java)
    assertThat(pager.updatedCount).isEqualTo(0)
    assertThat(pager.remainingUpdates).hasSize(5)
    
    pager.execute()
    
    assertThat(pager.updatedCount).isEqualTo(5)
    assertThat(pager.remainingUpdates).isEmpty()
  }

  class AsyncAlbumTrackWritingPagerHandler(
    private val playlistToken: String,
    private val musicTable: app.cash.tempest2.musiclibrary.AsyncMusicTable,
    /** Offset the current version by this amount. Use a non-zero value to induce failure */
    private val currentVersionOffset: Int = 0,
  ) : AsyncWritingPager.Handler<AlbumTrack.Key> {
    private lateinit var currentPagePlaylistInfo: PlaylistInfo
    private lateinit var currentPageTracks: List<AlbumTrack.Key>

    var eachPageCounter = 0
    var beforePageCounter = 0
    // Records a count of items added by page number
    var itemCounterMap : MutableMap<Int, Int> = mutableMapOf()
    var finishPageCounter = 0
    var pageWrittenCounter = 0

    val written: MutableList<TransactionWriteSet> = mutableListOf()

    override suspend fun eachPage(proceed: suspend () -> Unit) {
      eachPageCounter++
      proceed()
    }

    override fun beforePage(
      remainingUpdates: List<AlbumTrack.Key>,
      maxTransactionItems: Int
    ): Int {
      beforePageCounter++
      // Reserve 1 for the playlist info at the end.
      currentPageTracks = remainingUpdates.take((maxTransactionItems - 1))
      currentPagePlaylistInfo = runBlocking { 
        musicTable.playlistInfo.load(PlaylistInfo.Key(playlistToken))!!
      }
      return currentPageTracks.size
    }

    override fun item(builder: TransactionWriteSet.Builder, item: AlbumTrack.Key) {
      // Increment the item count for this page
      itemCounterMap.merge(pageWrittenCounter, 1, Integer::sum)
      builder.checkCondition(item, trackExists())
    }

    override fun finishPage(builder: TransactionWriteSet.Builder) {
      finishPageCounter++
      val playlistInfo = currentPagePlaylistInfo
      builder.save(
        playlistInfo.copy(
          playlist_tracks = playlistInfo.playlist_tracks + currentPageTracks,
          playlist_version = playlistInfo.playlist_version + 1
        ),
        ifPlaylistVersionIs(playlistInfo.playlist_version + currentVersionOffset)
      )
    }

    override fun pageWritten(writeSet: TransactionWriteSet) {
      pageWrittenCounter++
      written.add(writeSet)
    }
  }
}

private fun ifPlaylistVersionIs(playlist_version: Long): Expression {
  return Expression.builder()
    .expression("playlist_version = :playlist_version")
    .expressionValues(
      mapOf(
        ":playlist_version" to AttributeValue.builder().n("$playlist_version").build()
      )
    )
    .build()
}

private fun trackExists(): Expression {
  return Expression.builder()
    .expression("attribute_exists(track_title)")
    .build()
}