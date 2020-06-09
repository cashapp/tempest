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
  fun primaryIndex() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val page = musicDb.albums.tracks.query(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_4"))
    assertThat(page.offset).isNull()
    assertThat(page.contents).containsAll(albumTracks)
  }

  @Test
  fun primaryIndexPagination() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val page1 = musicDb.albums.tracks.query(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_4"),
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(albumTracks.slice(0..1))
    val page2 = musicDb.albums.tracks.query(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_4"),
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks.slice(2..2))
  }

  @Test
  fun primaryIndexDesc() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val page = musicDb.albums.tracks.query(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_4"),
        asc = false)
    assertThat(page.offset).isNull()
    assertThat(page.contents).containsAll(albumTracks.reversed())
  }

  @Test
  fun primaryIndexDescPagination() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val page1 = musicDb.albums.tracks.query(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_4"),
        asc = false,
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(albumTracks.reversed().slice(0..1))
    val page2 = musicDb.albums.tracks.query(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_4"),
        asc = false,
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(albumTracks.reversed().slice(2..2))
  }

  @Test
  fun localSecondaryIndex() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val page = musicDb.albums.tracksByName.query(
        AlbumTrackByNameOffset("M_1", "e"),
        AlbumTrackByNameOffset("M_1", "z"))
    assertThat(page.offset).isNull()
    assertThat(page.contents).contains(albumTracks[1], albumTracks[2])
  }

  @Test
  fun localSecondaryIndexPagination() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val page1 = musicDb.albums.tracksByName.query(
        AlbumTrackByNameOffset("M_1", " "),
        AlbumTrackByNameOffset("M_1", "~"),
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(albumTracks.slice(0..1))
    val page2 = musicDb.albums.tracksByName.query(
        AlbumTrackByNameOffset("M_1", " "),
        AlbumTrackByNameOffset("M_1", "~"),
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
        AlbumArtistByArtistOffset("ARTIST_1", "M_"),
        AlbumArtistByArtistOffset("ARTIST_1", "M`"))
    assertThat(artist1Page.offset).isNull()
    assertThat(artist1Page.contents).containsAll(artist1Albums)
    val artist2Page = musicDb.albums.albumArtistByArtist.query(
        AlbumArtistByArtistOffset("ARTIST_2", "M_"),
        AlbumArtistByArtistOffset("ARTIST_2", "M`"))
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
        AlbumArtistByArtistOffset("ARTIST_1", "M_"),
        AlbumArtistByArtistOffset("ARTIST_1", "M`"),
        pageSize = 2)
    assertThat(page1.offset).isNotNull()
    assertThat(page1.contents).containsAll(artist1Albums.slice(0..1))
    val page2 = musicDb.albums.albumArtistByArtist.query(
        AlbumArtistByArtistOffset("ARTIST_1", "M_"),
        AlbumArtistByArtistOffset("ARTIST_1", "M`"),
        pageSize = 2,
        initialOffset = page1.offset)
    assertThat(page2.offset).isNull()
    assertThat(page2.contents).containsAll(artist1Albums.slice(2..2))
  }
}
