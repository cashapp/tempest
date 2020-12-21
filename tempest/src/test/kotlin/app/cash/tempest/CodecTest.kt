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

import app.cash.tempest.musiclibrary.AlbumInfo
import app.cash.tempest.musiclibrary.AlbumTrack
import app.cash.tempest.musiclibrary.MusicDb
import app.cash.tempest.musiclibrary.MusicDbTestModule
import app.cash.tempest.musiclibrary.MusicItem
import java.time.LocalDate
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class CodecTest {

  @MiskTestModule
  val module = MusicDbTestModule()

  @Inject
  lateinit var musicDb: MusicDb

  @Test
  internal fun itemCodecToDb() {
    val albumInfoCodec = musicDb.music.codec(AlbumInfo::class)

    val albumInfo = AlbumInfo(
        "ALBUM_1",
        "after hours - EP",
        "53 Thieves",
        LocalDate.of(2020, 2, 21),
        "Contemporary R&B"
    )

    val musicItem = albumInfoCodec.toDb(albumInfo)
    assertThat(musicItem.partition_key).isEqualTo("ALBUM_1")
    assertThat(musicItem.sort_key).isEqualTo("INFO_")
    assertThat(musicItem.album_title).isEqualTo("after hours - EP")
    assertThat(musicItem.artist_name).isEqualTo("53 Thieves")
    assertThat(musicItem.release_date).isEqualTo(LocalDate.of(2020, 2, 21))
    assertThat(musicItem.genre_name).isEqualTo("Contemporary R&B")
    assertThat(musicItem.run_length).isNull()
    assertThat(musicItem.track_title).isNull()
    assertThat(musicItem.playlist_name).isNull()
    assertThat(musicItem.track_token).isNull()
  }

  @Test
  internal fun itemCodecToApp() {
    val albumInfoCodec = musicDb.music.codec(AlbumInfo::class)

    val musicItem = MusicItem().apply {
      partition_key = "ALBUM_1"
      sort_key = "INFO_"
      album_title = "after hours - EP"
      artist_name = "53 Thieves"
      release_date = LocalDate.of(2020, 2, 21)
      genre_name = "Contemporary R&B"
    }

    val albumInfo = albumInfoCodec.toApp(musicItem)
    assertThat(albumInfo).isEqualTo(
        AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        )
    )
  }

  @Test
  internal fun keyCodecToDb() {
    val albumKeyCodec = musicDb.music.codec(AlbumTrack.Key::class)

    val albumTrackKey = AlbumTrack.Key(
        album_token = "ALBUM_1",
        track_number = 1L
    )

    val musicItem = albumKeyCodec.toDb(albumTrackKey)
    assertThat(musicItem.partition_key).isEqualTo("ALBUM_1")
    assertThat(musicItem.sort_key).isEqualTo("TRACK_0000000000000001")
    assertThat(musicItem.album_title).isNull()
    assertThat(musicItem.run_length).isNull()
    assertThat(musicItem.track_title).isNull()
    assertThat(musicItem.playlist_name).isNull()
    assertThat(musicItem.track_token).isNull()
  }

  @Test
  internal fun keyCodecToApp() {
    val musicItem = MusicItem().apply {
      partition_key = "ALBUM_1"
      sort_key = "TRACK_0000000000000001"
    }

    val albumKeyCodec = musicDb.music.codec(AlbumTrack.Key::class)

    val albumTrackKey = albumKeyCodec.toApp(musicItem)
    assertThat(albumTrackKey).isEqualTo(
        AlbumTrack.Key(
            album_token = "ALBUM_1",
            track_number = 1L
        )
    )
  }

  @Test
  internal fun unexpectedType() {
    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.codec(AlbumColor::class)
    }.withMessageContaining("unexpected type")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.codec(AlbumColor.Key::class)
    }.withMessageContaining("unexpected type")
  }
}

data class AlbumColor(
  @Attribute(name = "partition_key")
  val album_token: String,
  val hex: String
) {
  @Attribute(prefix = "COLOR_")
  val sort_key: String = ""

  data class Key(
    val album_token: String
  ) {
    val sort_key: String = ""
  }
}
