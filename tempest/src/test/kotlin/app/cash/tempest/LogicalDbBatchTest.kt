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

package app.cash.tempest

import app.cash.tempest.example.AlbumTrack
import app.cash.tempest.example.AlbumTrackKey
import app.cash.tempest.example.MusicDb
import app.cash.tempest.example.MusicDbTestModule
import app.cash.tempest.example.PlaylistEntry
import app.cash.tempest.example.PlaylistEntryKey
import app.cash.tempest.example.PlaylistInfo
import app.cash.tempest.example.PlaylistInfoKey
import java.time.Duration
import javax.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class LogicalDbBatchTest {
  @MiskTestModule
  val module = MusicDbTestModule()
  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb

  @Test
  fun batchLoad() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"))
    assertThat(items).containsAll(albumTracks)
  }

  @Test
  fun batchLoadMultipleTables() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }
    val playlistInfo = PlaylistInfo("L_1", "WFH Music", 1)
    musicDb.playlists.info.save(playlistInfo)
    val playlistEntry = PlaylistEntry("L_1", "M_1:T_1")
    musicDb.playlists.entries.save(playlistEntry)
    val playlistEntry2 = PlaylistEntry("L_1", "M_3:T_2")
    musicDb.playlists.entries.save(playlistEntry2)

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"),
        PlaylistInfoKey("L_1"),
        PlaylistEntryKey("L_1", "M_1:T_1"),
        PlaylistEntryKey("L_1", "M_3:T_2"))
    assertThat(items.getItems<AlbumTrack>()).containsAll(albumTracks)
    assertThat(items.getItems<PlaylistInfo>()).contains(playlistInfo)
    assertThat(items.getItems<PlaylistEntry>()).contains(playlistEntry, playlistEntry2)
  }

  @Test
  fun batchLoadAfterBatchWrite() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    musicDb.batchWrite(BatchWriteSet.Builder().clobber(albumTracks).build())

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"))
    assertThat(items).containsAll(albumTracks)
  }

  @Test
  fun batchLoadAfterBatchDelete() {
    val t1 = AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S"))
    val t2 = AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S"))
    val t3 = AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    musicDb.batchWrite(BatchWriteSet.Builder().clobber(t1, t2, t3).build())

    musicDb.batchWrite(BatchWriteSet.Builder().delete(AlbumTrackKey("M_1", "T_2")).build())

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"))
    assertThat(items).contains(t1, t3)
  }
}
