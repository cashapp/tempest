package misk.logicaldb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.logicaldb.example.AlbumArtist
import misk.logicaldb.example.AlbumArtistKey
import misk.logicaldb.example.AlbumInfo
import misk.logicaldb.example.AlbumInfoKey
import misk.logicaldb.example.Artist
import misk.logicaldb.example.MusicDb
import misk.logicaldb.example.MusicDbTestModule
import misk.logicaldb.example.PlaylistInfo
import misk.logicaldb.example.PlaylistInfoKey
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class DynamoDbViewTest {
  @MiskTestModule
  val module = MusicDbTestModule()
  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb

  @Test
  fun loadAfterSave() {
    val albumInfo =
        AlbumInfo("M_1", "after hours - EP", LocalDate.of(2020, 2, 21), "Contemporary R&B")
    musicDb.albums.info.save(albumInfo)
    val artist = Artist("ARTIST_1", "53 Thieves")
    musicDb.artists.artists.save(artist)
    val albumArtist = AlbumArtist("M_1", "ARTIST_1")
    musicDb.albums.artists.save(albumArtist)

    // Query the movies created.
    val loadedAlbumInfo = musicDb.albums.info.load(AlbumInfoKey("M_1"))!!
    assertThat(loadedAlbumInfo.album_token).isEqualTo(albumInfo.album_token)
    assertThat(loadedAlbumInfo.album_name).isEqualTo(albumInfo.album_name)
    assertThat(loadedAlbumInfo.release_date).isEqualTo(albumInfo.release_date)
    assertThat(loadedAlbumInfo.genre_name).isEqualTo(albumInfo.genre_name)

    val loadedAlbumArtist = musicDb.albums.artists.load(
        AlbumArtistKey("M_1", "ARTIST_1"))!!
    assertThat(loadedAlbumArtist.album_token).isEqualTo(albumArtist.album_token)
    assertThat(loadedAlbumArtist.artist_token).isEqualTo(albumArtist.artist_token)
  }

  @Test
  fun saveIfNotExist() {
    val albumInfo =
        AlbumInfo("M_1", "after hours - EP", LocalDate.of(2020, 2, 21), "Contemporary R&B")
    musicDb.albums.info.save(albumInfo,
        DynamoDBSaveExpression()
            .withExpectedEntry("album_token", ExpectedAttributeValue().withExists(false)))

    // This fails because the album info already exists.
    assertThatExceptionOfType(ConditionalCheckFailedException::class.java)
        .isThrownBy {
          musicDb.albums.info.save(albumInfo,
              DynamoDBSaveExpression()
                  .withExpectedEntry("album_token", ExpectedAttributeValue().withExists(false)))
        }
  }

  @Test
  fun optimisticLocking() {
    val previousPlaylistInfo = PlaylistInfo("L_1", "WFH Music", 0)
    musicDb.playlists.info.save(previousPlaylistInfo)

    // Update PlaylistInfo only if playlist_size is 0.
    val playlistInfo = previousPlaylistInfo.copy(
        playlist_size = 1
    )
    musicDb.playlists.info.save(playlistInfo, DynamoDBSaveExpression()
        .withExpectedEntry("playlist_size",
            ExpectedAttributeValue()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(AttributeValue().withN("${previousPlaylistInfo.playlist_size}"))))

    val loadedPlaylistInfo = musicDb.playlists.info.load(PlaylistInfoKey("L_1"))!!
    assertThat(loadedPlaylistInfo.playlist_token).isEqualTo(playlistInfo.playlist_token)
    assertThat(loadedPlaylistInfo.playlist_size).isEqualTo(playlistInfo.playlist_size)

    // This fails because playlist_size is already 1.
    assertThatExceptionOfType(ConditionalCheckFailedException::class.java)
        .isThrownBy {
          musicDb.playlists.info.save(playlistInfo, DynamoDBSaveExpression()
              .withExpectedEntry("playlist_size",
                  ExpectedAttributeValue()
                      .withComparisonOperator(ComparisonOperator.EQ)
                      .withAttributeValueList(AttributeValue().withN("${previousPlaylistInfo.playlist_size}"))))
        }
  }

  @Test
  fun delete() {
    val albumInfo =
        AlbumInfo("M_1", "after hours - EP", LocalDate.of(2020, 2, 21), "Contemporary R&B")
    musicDb.albums.info.save(albumInfo)
    val artist = Artist("ARTIST_1", "53 Thieves")
    musicDb.artists.artists.save(artist)
    val albumArtist = AlbumArtist("M_1", "ARTIST_1")
    musicDb.albums.artists.save(albumArtist)

    musicDb.albums.info.deleteKey(AlbumInfoKey("M_1"))
    musicDb.albums.artists.delete(albumArtist)

    val loadedAlbumInfo = musicDb.albums.info.load(AlbumInfoKey("M_1"))
    assertThat(loadedAlbumInfo).isNull()
    val loadedAlbumArtist = musicDb.albums.artists.load(
        AlbumArtistKey("M_1", "ARTIST_1"))
    assertThat(loadedAlbumArtist).isNull()
  }
}
