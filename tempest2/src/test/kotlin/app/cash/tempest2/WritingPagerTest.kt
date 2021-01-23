/*
 * Copyright 2020 Square Inc.
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
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.MusicDbTestModule
import app.cash.tempest2.musiclibrary.MusicTable
import app.cash.tempest2.musiclibrary.PlaylistInfo
import app.cash.tempest2.musiclibrary.givenAlbums
import javax.inject.Inject
import misk.aws2.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@MiskTest(startService = true)
class WritingPagerTest {

  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb

  private val musicTable get() = musicDb.music

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
        ifPlaylistVersionIs(playlistInfo.playlist_version))
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
