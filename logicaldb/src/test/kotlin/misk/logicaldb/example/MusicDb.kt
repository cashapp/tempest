package misk.logicaldb.example

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.inject.KAbstractModule
import misk.logicaldb.LogicalDb

interface MusicDb : LogicalDb {
  val users: UserTable
  val playlists: PlaylistTable
  val albums: AlbumTable
  val artists: ArtistTable
}

class MusicDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DockerDynamoDbModule(DyUser::class, DyPlaylist::class, DyAlbum::class, DyArtist::class))
  }

  @Provides
  @Singleton
  fun providePlaylistDb(amazonDynamoDB: AmazonDynamoDB): MusicDb {
    val dynamoDbMapper = DynamoDBMapper(amazonDynamoDB)
    return LogicalDb(dynamoDbMapper)
  }
}
