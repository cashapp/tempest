package app.cash.tempest2.guides

import app.cash.tempest2.LogicalDb
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.PlaylistInfo
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class DataModeling(private val db: MusicDb) {
  fun logicalDbUsage1() {
    val items = db.batchLoad(
      AlbumTrack.Key("ALBUM_1", "TRACK_5"),
      AlbumTrack.Key("ALBUM_2", "TRACK_3"),
      PlaylistInfo.Key("PLAYLIST_1")
    )
  }

  fun logicalDbUsage2() {
    val enhancedClient = DynamoDbEnhancedClient.create()
    val db: MusicDb = LogicalDb(enhancedClient)
  }

  fun logicalDbOptionalConfiguration() {
    val client = DynamoDbClient.create()
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(client)
      .extensions(listOf(/* ... */))
      .build()
    val db: MusicDb = LogicalDb(enhancedClient)
  }
}
