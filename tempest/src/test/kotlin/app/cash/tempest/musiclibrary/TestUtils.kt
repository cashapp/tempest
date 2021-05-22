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

package app.cash.tempest.musiclibrary

import app.cash.tempest.Page
import app.cash.tempest.reservedwords.ReservedWordsItem
import app.cash.tempest.testing.JvmDynamoDbServer
import app.cash.tempest.testing.TestDynamoDb
import app.cash.tempest.testing.TestTable
import app.cash.tempest.versionedattribute.VersionedAttributeItem
import com.amazonaws.services.dynamodbv2.model.Projection
import com.amazonaws.services.dynamodbv2.model.ProjectionType

fun testDb() = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
  .addTable(
    TestTable.create<MusicItem> {
      for (gsi in it.globalSecondaryIndexes) {
        gsi.withProjection(Projection().withProjectionType(ProjectionType.ALL))
      }
      it
    }
  )
  .addTable(TestTable.create<ReservedWordsItem>())
  .addTable(TestTable.create<VersionedAttributeItem>())
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
