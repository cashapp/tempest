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

import app.cash.tempest.example.AlbumInfo
import app.cash.tempest.example.AlbumTrack
import app.cash.tempest.example.MusicDbTestModule
import app.cash.tempest.example.PlaylistInfo
import app.cash.tempest.example.TestDb
import app.cash.tempest.example.key
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue
import java.time.LocalDate
import javax.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbViewTest {
  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var testDb: TestDb

  private val musicTable get() = testDb.music

  @Test
  fun loadAfterSave() {
    val albumInfo = AlbumInfo(
      "ALBUM_1",
      "after hours - EP",
      "53 Thieves",
      LocalDate.of(2020, 2, 21),
      "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo)

    // Query the movies created.
    val loadedAlbumInfo = musicTable.albumInfo.load(albumInfo.key)!!

    assertThat(loadedAlbumInfo.album_token).isEqualTo(albumInfo.album_token)
    assertThat(loadedAlbumInfo.artist_name).isEqualTo(albumInfo.artist_name)
    assertThat(loadedAlbumInfo.release_date).isEqualTo(albumInfo.release_date)
    assertThat(loadedAlbumInfo.genre_name).isEqualTo(albumInfo.genre_name)
  }

  @Test
  fun saveIfNotExist() {
    val albumInfo = AlbumInfo(
      "ALBUM_1",
      "after hours - EP",
      "53 Thieves",
      LocalDate.of(2020, 2, 21),
      "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo, ifNotExist())

    // This fails because the album info already exists.
    assertThatExceptionOfType(ConditionalCheckFailedException::class.java)
      .isThrownBy {
        musicTable.albumInfo.save(albumInfo, ifNotExist())
      }
  }

  @Test
  fun optimisticLocking() {
    val playlistInfoV1 = PlaylistInfo(
      "PLAYLIST_1",
      "WFH Music",
      listOf(AlbumTrack.Key("ALBUM_1", 1), AlbumTrack.Key("ALBUM_3", 2))
    )
    musicTable.playlistInfo.save(playlistInfoV1)

    // Update PlaylistInfo only if playlist_version is 0.
    val playlistInfoV2 = playlistInfoV1.copy(
      playlist_name = "WFH Forever Music",
      playlist_version = 2
    )
    musicTable.playlistInfo.save(
      playlistInfoV2,
      ifPlaylistVersionIs(playlistInfoV1.playlist_version)
    )

    val actualPlaylistInfoV2 = musicTable.playlistInfo.load(PlaylistInfo.Key("PLAYLIST_1"))!!
    assertThat(actualPlaylistInfoV2).isEqualTo(playlistInfoV2)

    // This fails because playlist_size is already 1.
    assertThatExceptionOfType(ConditionalCheckFailedException::class.java)
      .isThrownBy {
        musicTable.playlistInfo.save(
          playlistInfoV2,
          ifPlaylistVersionIs(playlistInfoV1.playlist_version)
        )
      }
  }

  @Test
  fun delete() {
    val albumInfo = AlbumInfo(
      "ALBUM_1",
      "after hours - EP",
      "53 Thieves",
      LocalDate.of(2020, 2, 21),
      "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo)

    musicTable.albumInfo.deleteKey(albumInfo.key)

    val loadedAlbumInfo = musicTable.albumInfo.load(albumInfo.key)
    assertThat(loadedAlbumInfo).isNull()
  }

  private fun ifNotExist(): DynamoDBSaveExpression {
    return DynamoDBSaveExpression()
      .withExpectedEntry("partition_key", ExpectedAttributeValue().withExists(false))
  }

  private fun ifPlaylistVersionIs(playlist_version: Long): DynamoDBSaveExpression {
    return DynamoDBSaveExpression()
      .withExpectedEntry(
        "playlist_version",
        ExpectedAttributeValue()
          .withComparisonOperator(ComparisonOperator.EQ)
          .withAttributeValueList(AttributeValue().withN("$playlist_version"))
      )
  }
}
