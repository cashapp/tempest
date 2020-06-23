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
