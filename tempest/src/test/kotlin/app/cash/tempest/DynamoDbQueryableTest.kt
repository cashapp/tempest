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

import app.cash.tempest.example.AlbumArtist
import app.cash.tempest.example.AlbumArtistByArtistOffset
import app.cash.tempest.example.AlbumTrack
import app.cash.tempest.example.AlbumTrackByNameOffset
import app.cash.tempest.example.AlbumTrackKey
import app.cash.tempest.example.MusicDb
import app.cash.tempest.example.MusicDbTestModule
import java.time.Duration
import javax.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbQueryableTest {
  @MiskTestModule
  val module = MusicDbTestModule()
  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb

  @Test
  fun primaryIndexBetween() {
    val albumTracks = givenAfterHoursAlbum()

    val page1 = musicDb.albums.tracks.query(
        keyCondition = Between(AlbumTrackKey("M_1", "T_1"), AlbumTrackKey("M_1", "T_1"))
    )
    assertThat(page1.offset).isNull()
    assertThat(page1.contents).containsAll(albumTracks.slice(0..0))

    val page2 = musicDb.albums.tracks.query(
      keyCondition = Between(AlbumTrackKey("M_1", "T_2"), AlbumTrackKey("M_1", "T_3"))
    )
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks.slice(1..2))

    val page3 = musicDb.albums.tracks.query(
      keyCondition = Between(AlbumTrackKey("M_1", "T_1"), AlbumTrackKey("M_1", "T_3"))
    )
    assertThat(page3.offset).isNull()
    assertThat(page3.contents).containsAll(albumTracks)
  }

  @Test
  fun primaryIndexBeginsWith() {
    val albumTracks = givenAfterHoursAlbum()

    val page1 = musicDb.albums.tracks.query(
      keyCondition = BeginsWith(AlbumTrackKey("M_1", ""))
    )
    assertThat(page1.offset).isNull()
    assertThat(page1.contents).containsAll(albumTracks)

    val page2 = musicDb.albums.tracks.query(
      keyCondition = BeginsWith(AlbumTrackKey("M_1", "T_"))
    )
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks)

    val page3 = musicDb.albums.tracks.query(
      keyCondition = BeginsWith(AlbumTrackKey("M_1", "T_3"))
    )
    assertThat(page3.offset).isNull()
    assertThat(page3.contents).containsAll(albumTracks.slice(2..2))
  }

  @Test
  fun primaryIndexPagination() {
    val albumTracks = givenAfterHoursAlbum()

    val page1 = musicDb.albums.tracks.query(
        keyCondition = BeginsWith(AlbumTrackKey("M_1", "")),
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(albumTracks.slice(0..1))
    val page2 = musicDb.albums.tracks.query(
        keyCondition = BeginsWith(AlbumTrackKey("M_1", "")),
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks.slice(2..2))
  }

  @Test
  fun primaryIndexDesc() {
    val albumTracks = givenAfterHoursAlbum()

    val page = musicDb.albums.tracks.query(
        keyCondition = BeginsWith(AlbumTrackKey("M_1", "")),
        asc = false)
    assertThat(page.offset).isNull()
    assertThat(page.contents).containsAll(albumTracks.reversed())
  }

  @Test
  fun primaryIndexDescPagination() {
    val albumTracks = givenAfterHoursAlbum()

    val page1 = musicDb.albums.tracks.query(
        keyCondition = BeginsWith(AlbumTrackKey("M_1", "")),
        asc = false,
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(albumTracks.reversed().slice(0..1))
    val page2 = musicDb.albums.tracks.query(
        keyCondition = BeginsWith(AlbumTrackKey("M_1", "")),
        asc = false,
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks.reversed().slice(2..2))
  }

  @Test
  fun localSecondaryIndex() {
    val albumTracks = givenAfterHoursAlbum()

    val page = musicDb.albums.tracksByName.query(
        keyCondition = Between(AlbumTrackByNameOffset("M_1", "e"), AlbumTrackByNameOffset("M_1", "z")))
    assertThat(page.offset).isNull()
    assertThat(page.contents).contains(albumTracks[1], albumTracks[2])
  }

  @Test
  fun localSecondaryIndexPagination() {
    val albumTracks = givenAfterHoursAlbum().sortedBy { it.track_name }

    val page1 = musicDb.albums.tracksByName.query(
        keyCondition = Between(AlbumTrackByNameOffset("M_1", " "), AlbumTrackByNameOffset("M_1", "~")),
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(albumTracks.slice(0..1))
    val page2 = musicDb.albums.tracksByName.query(
        keyCondition = Between(AlbumTrackByNameOffset("M_1", " "), AlbumTrackByNameOffset("M_1", "~")),
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks.slice(2..2))
  }

  @Test
  fun globalSecondaryIndex() {
    val artist1Albums = listOf(
        AlbumArtist("M_1", "ARTIST_1"),
        AlbumArtist("M_2", "ARTIST_1"),
        AlbumArtist("M_3", "ARTIST_1")
    )
    val artist2Albums = listOf(
        AlbumArtist("M_1", "ARTIST_2"),
        AlbumArtist("M_5", "ARTIST_2"),
        AlbumArtist("M_6", "ARTIST_2"),
        AlbumArtist("M_8", "ARTIST_2"),
        AlbumArtist("M_9", "ARTIST_2")
    )
    val artist3Albums = listOf(
        AlbumArtist("M_3", "ARTIST_3")
    )
    for (albumArtist in artist1Albums + artist2Albums + artist3Albums) {
      musicDb.albums.artists.save(albumArtist)
    }

    val artist1Page = musicDb.albums.albumArtistByArtist.query(
        BeginsWith(AlbumArtistByArtistOffset("ARTIST_1", "M_")))
    assertThat(artist1Page.offset).isNull()
    assertThat(artist1Page.contents).containsAll(artist1Albums)
    val artist2Page = musicDb.albums.albumArtistByArtist.query(
      BeginsWith(AlbumArtistByArtistOffset("ARTIST_2", "M_")))
    assertThat(artist2Page.offset).isNull()
    assertThat(artist2Page.contents).containsAll(artist2Albums)
  }

  @Test
  fun globalSecondaryIndexPagination() {
    val artist1Albums = listOf(
        AlbumArtist("M_1", "ARTIST_1"),
        AlbumArtist("M_2", "ARTIST_1"),
        AlbumArtist("M_3", "ARTIST_1")
    )
    val artist2Albums = listOf(
        AlbumArtist("M_1", "ARTIST_2"),
        AlbumArtist("M_5", "ARTIST_2"),
        AlbumArtist("M_6", "ARTIST_2"),
        AlbumArtist("M_8", "ARTIST_2"),
        AlbumArtist("M_9", "ARTIST_2")
    )
    val artist3Albums = listOf(
        AlbumArtist("M_3", "ARTIST_3")
    )
    for (albumArtist in artist1Albums + artist2Albums + artist3Albums) {
      musicDb.albums.artists.save(albumArtist)
    }

    val page1 = musicDb.albums.albumArtistByArtist.query(
        keyCondition = Between(AlbumArtistByArtistOffset("ARTIST_1", "M_"), AlbumArtistByArtistOffset("ARTIST_1", "M`")),
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(artist1Albums.slice(0..1))
    val page2 = musicDb.albums.albumArtistByArtist.query(
        keyCondition = Between(AlbumArtistByArtistOffset("ARTIST_1", "M_"), AlbumArtistByArtistOffset("ARTIST_1", "M`")),
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(artist1Albums.slice(2..2))
  }

  private fun givenAfterHoursAlbum(): List<AlbumTrack> {
    val albumTracks = listOf(
      AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
      AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
      AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }
    return albumTracks
  }
}
