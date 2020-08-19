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

import app.cash.tempest.musiclibrary.MusicTable
import app.cash.tempest.musiclibrary.PlaylistInfo
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue

class OptimisticLocking(
  private val table: MusicTable
) {
  fun changePlaylistName(playlistToken: String, newName: String) {
    // Read.
    val existing = checkNotNull(
      table.playlistInfo.load(PlaylistInfo.Key(playlistToken))
    ) { "Playlist does not exist: $playlistToken" }
    // Modify.
    val newPlaylist = existing.copy(
      playlist_name = newName,
      playlist_version = existing.playlist_version + 1
    )
    // Write.
    table.playlistInfo.save(
      newPlaylist,
      ifPlaylistVersionIs(existing.playlist_version)
    )
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
