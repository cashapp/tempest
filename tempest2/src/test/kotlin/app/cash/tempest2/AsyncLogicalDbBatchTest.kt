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
import app.cash.tempest2.musiclibrary.AsyncMusicDb
import app.cash.tempest2.musiclibrary.PlaylistInfo
import app.cash.tempest2.musiclibrary.testDb
import app.cash.tempest2.testing.asyncLogicalDb
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class AsyncLogicalDbBatchTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicDb by lazy { db.asyncLogicalDb<AsyncMusicDb>() }
  private val musicTable by lazy { musicDb.music }

  @Test
  fun batchLoad() = runBlocking {
    val albumTracks = listOf(
      AlbumTrack("ALBUM_1", 1, "dreamin'", Duration.parse("PT3M28S")),
      AlbumTrack("ALBUM_1", 2, "what you do to me", Duration.parse("PT3M24S")),
      AlbumTrack("ALBUM_1", 3, "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicTable.albumTracks.save(albumTrack)
    }
    val playlistInfo = PlaylistInfo(
      playlist_token = "PLAYLIST_1",
      playlist_name = "WFH Music",
      playlist_tracks = listOf(AlbumTrack.Key("ALBUM_1", 1))
    )
    musicTable.playlistInfo.save(playlistInfo)

    val loadedItems = musicDb.batchLoad(
      PlaylistInfo.Key("PLAYLIST_1"),
      AlbumTrack.Key("ALBUM_1", track_number = 1),
      AlbumTrack.Key("ALBUM_1", track_number = 2),
      AlbumTrack.Key("ALBUM_1", track_number = 3)
    )
    assertThat(loadedItems.getItems<AlbumTrack>()).containsAll(albumTracks)
    assertThat(loadedItems.getItems<PlaylistInfo>()).containsExactly(playlistInfo)
  }

  @Test
  fun batchLoadMultipleTables() = runBlocking {
    val albumTracks = listOf(
      AlbumTrack("ALBUM_1", 1, "dreamin'", Duration.parse("PT3M28S")),
      AlbumTrack("ALBUM_1", 2, "what you do to me", Duration.parse("PT3M24S")),
      AlbumTrack("ALBUM_1", 3, "too slow", Duration.parse("PT2M27S"))
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

    val items = musicDb.batchLoad(
      AlbumTrack.Key("ALBUM_1", track_number = 1),
      AlbumTrack.Key("ALBUM_1", track_number = 2),
      AlbumTrack.Key("ALBUM_1", track_number = 3),
      PlaylistInfo.Key("PLAYLIST_1")
    )
    assertThat(items.getItems<AlbumTrack>()).containsAll(albumTracks)
    assertThat(items.getItems<PlaylistInfo>()).containsExactly(playlistInfo)
  }

  @Test
  fun batchLoadAfterBatchWrite() = runBlocking {
    val albumTracks = listOf(
      AlbumTrack("ALBUM_1", 1, "dreamin'", Duration.parse("PT3M28S")),
      AlbumTrack("ALBUM_1", 2, "what you do to me", Duration.parse("PT3M24S")),
      AlbumTrack("ALBUM_1", 3, "too slow", Duration.parse("PT2M27S"))
    )
    val result = musicDb.batchWrite(BatchWriteSet.Builder().clobber(albumTracks).build())
    assertThat(result.isSuccessful).isTrue()

    val items = musicDb.batchLoad(
      AlbumTrack.Key("ALBUM_1", track_number = 1),
      AlbumTrack.Key("ALBUM_1", track_number = 2),
      AlbumTrack.Key("ALBUM_1", track_number = 3)
    )
    assertThat(items).containsAll(albumTracks)
  }

  @Test
  fun batchLoadAfterBatchDelete() = runBlocking {
    val t1 = AlbumTrack("ALBUM_1", 1, "dreamin'", Duration.parse("PT3M28S"))
    val t2 = AlbumTrack("ALBUM_1", 2, "what you do to me", Duration.parse("PT3M24S"))
    val t3 = AlbumTrack("ALBUM_1", 3, "too slow", Duration.parse("PT2M27S"))

    val result1 = musicDb.batchWrite(BatchWriteSet.Builder().clobber(t1, t2, t3).build())
    assertThat(result1.isSuccessful).isTrue()
    val result2 = musicDb.batchWrite(BatchWriteSet.Builder().delete(AlbumTrack.Key("ALBUM_1", 2)).build())
    assertThat(result2.isSuccessful).isTrue()

    val items = musicDb.batchLoad(
      AlbumTrack.Key("ALBUM_1", track_number = 1),
      AlbumTrack.Key("ALBUM_1", track_number = 2),
      AlbumTrack.Key("ALBUM_1", track_number = 3)
    )
    assertThat(items).containsExactly(t3, t1)
  }
}
