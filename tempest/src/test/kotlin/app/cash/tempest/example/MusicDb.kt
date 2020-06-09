package app.cash.tempest.example

import app.cash.tempest.LogicalDb
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.inject.KAbstractModule

interface MusicDb : LogicalDb {
  val playlists: PlaylistTable
  val albums: AlbumTable
  val artists: ArtistTable
}

class MusicDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DockerDynamoDbModule(DyPlaylist::class, DyAlbum::class, DyArtist::class))
  }

  @Provides
  @Singleton
  fun providePlaylistDb(amazonDynamoDB: AmazonDynamoDB): MusicDb {
    val dynamoDbMapper = DynamoDBMapper(amazonDynamoDB)
    return LogicalDb(dynamoDbMapper)
  }
}
