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

package app.cash.tempest.guides

import app.cash.tempest.musiclibrary.AlbumInfo
import app.cash.tempest.musiclibrary.MusicTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue

class Crud(
  private val table: MusicTable
) {

  fun getAlbumTitle(albumToken: String): String? {
    val albumInfo = table.albumInfo.load(AlbumInfo.Key(albumToken)) ?: return null
    return albumInfo.album_title
  }

  fun consistentlyGetAlbumTitle(albumToken: String): String? {
    val albumInfo = table.albumInfo.load(
      AlbumInfo.Key(albumToken),
      consistentReads = ConsistentReads.CONSISTENT
    ) ?: return null
    return albumInfo.album_title
  }

  fun addAlbum(albumInfo: AlbumInfo) {
    table.albumInfo.save(albumInfo)
  }

  fun addAlbumIfNotExist(albumInfo: AlbumInfo) {
    table.albumInfo.save(albumInfo, ifNotExist())
  }

  private fun ifNotExist(): DynamoDBSaveExpression {
    return DynamoDBSaveExpression()
      .withExpectedEntry("partition_key", ExpectedAttributeValue().withExists(false))
  }
}
