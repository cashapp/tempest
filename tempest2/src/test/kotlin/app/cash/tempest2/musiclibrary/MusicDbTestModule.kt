/*
 * Copyright 2021 Square Inc.
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

package app.cash.tempest2.musiclibrary

import app.cash.tempest2.LogicalDb
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DockerDynamoDbModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.inject.KAbstractModule
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedLocalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.Projection
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput

class MusicDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(
      DockerDynamoDbModule(
        DynamoDbTable("music_items", MusicItem::class) {
          it.globalSecondaryIndices(
            EnhancedGlobalSecondaryIndex.builder()
              .indexName("genre_album_index")
              .projection(
                Projection.builder()
                  .projectionType("ALL")
                  .build()
              )
              .provisionedThroughput(
                ProvisionedThroughput.builder()
                  .readCapacityUnits(1)
                  .writeCapacityUnits(1)
                  .build()
              )
              .build(),
            EnhancedGlobalSecondaryIndex.builder()
              .indexName("artist_album_index")
              .projection(
                Projection.builder()
                  .projectionType("ALL")
                  .build()
              )
              .provisionedThroughput(
                ProvisionedThroughput.builder()
                  .readCapacityUnits(1)
                  .writeCapacityUnits(1)
                  .build()
              )
              .build()
          )
            .localSecondaryIndices(
              EnhancedLocalSecondaryIndex.create(
                "album_track_title_index",
                Projection.builder()
                  .projectionType("ALL")
                  .build()
              )
            )
        }
      )
    )
  }

  @Provides
  @Singleton
  fun provideTestMusicDb(dynamoDbClient: DynamoDbClient): MusicDb {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDbClient)
      .build()
    return LogicalDb(enhancedClient)
  }
}
