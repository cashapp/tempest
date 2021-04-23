package app.cash.tempest.guides

import app.cash.tempest.LogicalDb
import app.cash.tempest.musiclibrary.AlbumTrack
import app.cash.tempest.musiclibrary.MusicDb
import app.cash.tempest.musiclibrary.PlaylistInfo
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig

class DataModeling(private val db: MusicDb) {
  fun logicalDbUsage1() {
    val items = db.batchLoad(
      AlbumTrack.Key("ALBUM_1", "TRACK_5"),
      AlbumTrack.Key("ALBUM_2", "TRACK_3"),
      PlaylistInfo.Key("PLAYLIST_1")
    )
  }

  fun logicalDbUsage2() {
    val client = AmazonDynamoDBClientBuilder.standard().build()
    val mapper = DynamoDBMapper(client)
    val db: MusicDb = LogicalDb(mapper)
  }

  fun logicalDbOptionalConfiguration() {
    val client = AmazonDynamoDBClientBuilder.standard().build()
    val mapperConfig = DynamoDBMapperConfig.builder()
      .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
      .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
      .withTableNameOverride(null)
      .withPaginationLoadingStrategy(DynamoDBMapperConfig.PaginationLoadingStrategy.EAGER_LOADING)
      .build()
    val mapper = DynamoDBMapper(client, mapperConfig)
    val db: MusicDb = LogicalDb(mapper)
  }
}