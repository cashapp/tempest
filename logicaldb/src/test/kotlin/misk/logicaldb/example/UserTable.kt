package misk.logicaldb.example

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import misk.logicaldb.Attribute
import misk.logicaldb.ForIndex
import misk.logicaldb.InlineView
import misk.logicaldb.LogicalTable
import misk.logicaldb.SecondaryIndex

interface UserTable : LogicalTable<DyUser> {
  val info: InlineView<UserInfoKey, UserInfo>
  val playlistFollows: InlineView<UserPlaylistFollowKey, UserPlaylistFollow>
  val albumTrackRatings: InlineView<UserAlbumTrackRatingKey, UserAlbumTrackRating>

  // Global Secondary Indexes.
  val playlistFollowsByPlaylist: SecondaryIndex<UserPlaylistFollowByPlaylistOffset, UserPlaylistFollow>

  // Local Secondary Indexes.
  val albumTrackRatingsByScore: SecondaryIndex<UserAlbumTrackRatingByScoreOffset, UserAlbumTrackRating>
}

data class UserInfoKey(
  val user_token: String
)

data class UserInfo(
  val user_token: String,
  val display_name: String
) {
  @Attribute(prefix = "INFO_")
  val range_key: String = ""
}

data class UserPlaylistFollowKey(
  val user_token: String,
  val playlist_token: String
)

@ForIndex("playlist_follower_index")
data class UserPlaylistFollowByPlaylistOffset(
  val playlist_token: String,
  val user_token: String
)

data class UserPlaylistFollow(
  val user_token: String,
  @Attribute(names = ["range_key", "playlist_follower_index_playlist_token"], prefix = "PF_")
  val playlist_token: String
)

data class UserAlbumTrackRatingKey(
  val user_token: String,
  val album_track_token: String
)

@ForIndex("user_album_track_rating_score_index")
data class UserAlbumTrackRatingByScoreOffset(
  val user_token: String,
  val score: Int,
  // To uniquely identify an item during pagination.
  val album_track_token: String? = null
)

data class UserAlbumTrackRating(
  val user_token: String,
  @Attribute(name = "range_key", prefix = "ATR_")
  val album_track_token: String,
  val score: Int
)

@DynamoDBTable(tableName = "users")
class DyUser {
  // All Rows.
  @DynamoDBHashKey
  @DynamoDBIndexRangeKey(globalSecondaryIndexName = "playlist_follower_index")
  var user_token: String? = null
  @DynamoDBRangeKey
  var range_key: String? = null

  // UserInfo.
  @DynamoDBAttribute
  var display_name: String? = null
  // UserPlaylistFollow.
  // For this type of items, this attribute is the same as the range key. This GSI indexes on this
  // attribute instead of on the range key because the range key is overloaded by other types of items.
  // In other words, we don't want to use
  // [GSI overloading](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/bp-gsi-overloading.html)
  // here.
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "playlist_follower_index")
  @DynamoDBAttribute
  var playlist_follower_index_playlist_token: String? = null

  // UserAlbumTrackRating.
  @DynamoDBIndexRangeKey(localSecondaryIndexName = "user_album_track_rating_score_index")
  @DynamoDBAttribute
  var score: Int? = null
}
