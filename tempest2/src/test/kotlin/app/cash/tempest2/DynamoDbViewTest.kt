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

import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.PlaylistInfo
import app.cash.tempest2.musiclibrary.testDb
import app.cash.tempest2.testing.logicalDb
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity
import java.time.LocalDate

class DynamoDbViewTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicTable by lazy { db.logicalDb<MusicDb>().music }

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
  fun loadWithCapacityAfterSave() {
    val albumInfo = AlbumInfo(
      "ALBUM_1",
      "after hours - EP",
      "53 Thieves",
      LocalDate.of(2020, 2, 21),
      "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo)

    // Query the movies created.
    val (loadedAlbumInfo, consumedCapacity) = musicTable.albumInfo.load(albumInfo.key, returnConsumedCapacity = ReturnConsumedCapacity.TOTAL)

    assertThat(loadedAlbumInfo!!.album_token).isEqualTo(albumInfo.album_token)
    assertThat(loadedAlbumInfo.artist_name).isEqualTo(albumInfo.artist_name)
    assertThat(loadedAlbumInfo.release_date).isEqualTo(albumInfo.release_date)
    assertThat(loadedAlbumInfo.genre_name).isEqualTo(albumInfo.genre_name)
    assertThat(consumedCapacity?.capacityUnits()).isGreaterThan(0.0)

    val (_, consumedCapacity2) = musicTable.albumInfo.load(
      albumInfo.key,
      returnConsumedCapacity = ReturnConsumedCapacity.NONE
    )
    assertThat(consumedCapacity2).isNull()
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
      listOf(
        AlbumTrack.Key("ALBUM_1", 1),
        AlbumTrack.Key("ALBUM_3", 2)
      )
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

    val deleted = musicTable.albumInfo.deleteKey(albumInfo.key)
    assertThat(deleted).isEqualTo(albumInfo)

    val loadedAlbumInfo = musicTable.albumInfo.load(albumInfo.key)
    assertThat(loadedAlbumInfo).isNull()
  }

  private fun ifNotExist(): Expression {
    return Expression.builder()
      .expression("attribute_not_exists(partition_key)")
      .build()
  }

  private fun ifPlaylistVersionIs(playlist_version: Long): Expression {
    return Expression.builder()
      .expression("playlist_version = :version")
      .expressionValues(
        mapOf(
          ":version" to AttributeValue.builder().n("$playlist_version").build()
        )
      )
      .build()
  }
}
