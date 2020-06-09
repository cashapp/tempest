package misk.logicaldb.example

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import misk.logicaldb.Attribute
import misk.logicaldb.InlineView
import misk.logicaldb.LogicalTable

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
