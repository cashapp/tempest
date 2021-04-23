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

package app.cash.tempest2.guides

import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.MusicTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import java.time.LocalDate

class Crud(
  private val table: MusicTable
) {

  // Read.
  fun getAlbumTitle(albumToken: String): String? {
    val albumInfo = table.albumInfo.load(AlbumInfo.Key(albumToken)) ?: return null
    return albumInfo.album_title
  }

  // Read - Eventual consistency.
  fun readAfterWrite() {
    // Write an item.
    val item = AlbumInfo(
      album_token = "ALBUM_cafcf892",
      album_title = "The Dark Side of the Moon",
      artist_name = "Pink Floyd",
      release_date = LocalDate.of(1973, 3, 1),
      genre_name = "Progressive rock"
    )
    table.albumInfo.save(item)
    // Read that item.
    val itemRead = table.albumInfo.load(item.key)
    // Note that the value we just read might be older than the value we wrote.
  }

  // Read - Strongly consistent.
  fun getAlbumTitle2(albumToken: String): String? {
    val albumInfo = table.albumInfo.load(
      AlbumInfo.Key(albumToken),
      consistentReads = true
    ) ?: return null
    return albumInfo.album_title
  }

  // Update.
  fun addAlbum(albumInfo: AlbumInfo) {
    table.albumInfo.save(albumInfo)
  }

  // Update - Conditional.
  fun addAlbum2(albumInfo: AlbumInfo) {
    table.albumInfo.save(albumInfo, ifNotExist())
  }

  private fun ifNotExist(): Expression {
    return Expression.builder()
      .expression("attribute_not_exists(partition_key)")
      .build()
  }

  // Delete.
  fun deleteAlbum(albumToken: String) {
    table.albumInfo.deleteKey(AlbumInfo.Key(albumToken))
  }

  // Delete - Conditional.
  fun deleteAlbum2(albumToken: String) {
    table.albumInfo.deleteKey(AlbumInfo.Key(albumToken), ifExist())
  }

  private fun ifExist(): Expression {
    return Expression.builder()
      .expression("attribute_exists(partition_key)")
      .build()
  }
}
