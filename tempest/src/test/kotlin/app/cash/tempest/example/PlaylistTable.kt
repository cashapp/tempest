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

package app.cash.tempest.example

import app.cash.tempest.Attribute
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

interface PlaylistTable : LogicalTable<DyPlaylist> {
  val info: InlineView<PlaylistInfoKey, PlaylistInfo>
  val entries: InlineView<PlaylistEntryKey, PlaylistEntry>
}

data class PlaylistInfoKey(
  val playlist_token: String
)

data class PlaylistInfo(
  val playlist_token: String,
  val playlist_name: String,
  val playlist_size: Int
) {
  @Attribute(prefix = "_INFO")
  val range_key: String = ""
}

data class PlaylistEntryKey(
  val playlist_token: String,
  val album_track_token: String
)

data class PlaylistEntry(
  val playlist_token: String,
  @Attribute(name = "range_key", prefix = "E_")
  val album_track_token: String
)

@DynamoDBTable(tableName = "playlists")
class DyPlaylist {
  // All Rows.
  @DynamoDBHashKey
  var playlist_token: String? = null
  @DynamoDBRangeKey
  var range_key: String? = null

  // PlaylistInfo.
  @DynamoDBAttribute
  var playlist_name: String? = null
  @DynamoDBAttribute
  var playlist_size: Int? = null

  // PlaylistEntry.
}
