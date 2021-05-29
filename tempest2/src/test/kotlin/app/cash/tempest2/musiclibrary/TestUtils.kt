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

package app.cash.tempest2.musiclibrary

import app.cash.tempest.musiclibrary.Album
import app.cash.tempest2.Page
import app.cash.tempest2.testing.JvmDynamoDbServer
import app.cash.tempest2.testing.TestDynamoDb
import app.cash.tempest2.testing.TestTable
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedLocalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.Projection
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput

fun testDb() = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
  .addTable(
    TestTable.create<MusicItem>("music_items") {
      it.toBuilder()
        .globalSecondaryIndices(
          EnhancedGlobalSecondaryIndex.builder()
            .indexName("genre_album_index")
            .projection(
              Projection.builder()
                .projectionType("ALL")
                .build()
            )
            .provisionedThroughput(
              ProvisionedThroughput.builder()
                .readCapacityUnits(1)
                .writeCapacityUnits(1)
                .build()
            )
            .build(),
          EnhancedGlobalSecondaryIndex.builder()
            .indexName("artist_album_index")
            .projection(
              Projection.builder()
                .projectionType("ALL")
                .build()
            )
            .provisionedThroughput(
              ProvisionedThroughput.builder()
                .readCapacityUnits(1)
                .writeCapacityUnits(1)
                .build()
            )
            .build()
        )
        .localSecondaryIndices(
          EnhancedLocalSecondaryIndex.create(
            "album_track_title_index",
            Projection.builder()
              .projectionType("ALL")
              .build()
          )
        )
        .build()
    }
  )
  .build()

val Page<*, AlbumTrack>.trackTitles: List<String>
  get() = contents.map { it.track_title }

val Page<*, AlbumInfo>.albumTitles: List<String>
  get() = contents.map { it.album_title }

fun MusicTable.givenAlbums(vararg albums: Album) {
  for (album in albums) {
    albumInfo.save(
      AlbumInfo(
        album.album_token,
        album.album_title,
        album.artist_name,
        album.release_date,
        album.genre_name
      )
    )
    for ((i, track) in album.tracks.withIndex()) {
      albumTracks.save(
        AlbumTrack(
          album.album_token,
          i + 1L,
          track.track_title,
          track.run_length
        )
      )
    }
  }
}

suspend fun AsyncMusicTable.givenAlbums(vararg albums: Album) {
  for (album in albums) {
    albumInfo.save(
      AlbumInfo(
        album.album_token,
        album.album_title,
        album.artist_name,
        album.release_date,
        album.genre_name
      )
    )
    for ((i, track) in album.tracks.withIndex()) {
      albumTracks.save(
        AlbumTrack(
          album.album_token,
          i + 1L,
          track.track_title,
          track.run_length
        )
      )
    }
  }
}
