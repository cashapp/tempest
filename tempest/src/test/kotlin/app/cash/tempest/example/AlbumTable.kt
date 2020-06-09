package app.cash.tempest.example

import app.cash.tempest.Attribute
import app.cash.tempest.ForIndex
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import app.cash.tempest.SecondaryIndex
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted
import java.time.Duration
import java.time.LocalDate

interface AlbumTable : LogicalTable<DyAlbum> {
  val info: InlineView<AlbumInfoKey, AlbumInfo>
  val tracks: InlineView<AlbumTrackKey, AlbumTrack>
  val artists: InlineView<AlbumArtistKey, AlbumArtist>

  // Global Secondary Indexes.
  val infoByGenre: SecondaryIndex<AlbumInfoByGenreOffset, AlbumInfo>
  val albumArtistByArtist: SecondaryIndex<AlbumArtistByArtistOffset, AlbumArtist>

  // Local Secondary Indexes.
  val tracksByName: SecondaryIndex<AlbumTrackByNameOffset, AlbumTrack>
}

data class AlbumInfoKey(
  val album_token: String
)

@ForIndex("genre_album_index")
data class AlbumInfoByGenreOffset(
  val genre_name: String,
  val album_token: String
)

data class AlbumInfo(
  val album_token: String,
  val album_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val range_key: String = ""
}

data class AlbumInfoName(
  val album_token: String,
  val album_name: String
) {
  @Attribute(prefix = "INFO_")
  val range_key: String = ""
}

data class AlbumTrackKey(
  val album_token: String,
  val track_token: String
)

@ForIndex("album_track_name_index")
data class AlbumTrackByNameOffset(
  val album_token: String,
  val track_name: String,
  // To uniquely identify an item during pagination.
  val track_token: String? = null
)

data class AlbumTrack(
  val album_token: String,
  @Attribute(name = "range_key", prefix = "T_")
  val track_token: String,
  val track_name: String,
  val track_run_length: Duration
)

data class AlbumArtistKey(
  val album_token: String,
  val artist_token: String
)

@ForIndex("artist_album_index")
data class AlbumArtistByArtistOffset(
  val artist_token: String,
  val album_token: String
)

data class AlbumArtist(
  val album_token: String,
  @Attribute(names = ["range_key", "artist_album_index_artist_token"], prefix = "A_")
  val artist_token: String
)

@DynamoDBTable(tableName = "albums")
class DyAlbum {
  // All Rows.
  @DynamoDBHashKey
  @DynamoDBIndexRangeKey(globalSecondaryIndexNames = ["genre_album_index", "artist_album_index"])
  var album_token: String? = null
  @DynamoDBRangeKey
  var range_key: String? = null

  // AlbumInfo.
  @DynamoDBAttribute
  var album_name: String? = null
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = LocalDateTypeConverter::class)
  var release_date: LocalDate? = null
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "genre_album_index")
  @DynamoDBAttribute
  var genre_name: String? = null

  // AlbumTrack.
  @DynamoDBIndexRangeKey(localSecondaryIndexName = "album_track_name_index")
  @DynamoDBAttribute
  var track_name: String? = null
  @DynamoDBTypeConverted(converter = DurationTypeConverter::class)
  @DynamoDBAttribute
  var track_run_length: Duration? = null

  // AlbumArtist.
  // For this type of items, this attribute is the same as the range key. This GSI indexes on this
  // attribute instead of on the range key because the range key is overloaded by other types of items.
  // In other words, we don't want to use
  // [GSI overloading](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/bp-gsi-overloading.html)
  // here.
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "artist_album_index")
  @DynamoDBAttribute
  var artist_album_index_artist_token: String? = null
}
