package app.cash.tempest.example

import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

interface ArtistTable : LogicalTable<DyArtist> {
  val artists: InlineView<ArtistKey, Artist>
}

data class ArtistKey(
  val artist_token: String
)

data class Artist(
  val artist_token: String,
  val name: String
)

@DynamoDBTable(tableName = "artists")
class DyArtist {
  // All Rows.
  @DynamoDBHashKey
  var artist_token: String? = null
  @DynamoDBAttribute
  var name: String? = null
}
