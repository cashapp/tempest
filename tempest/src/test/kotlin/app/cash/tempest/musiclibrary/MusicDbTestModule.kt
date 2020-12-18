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

package app.cash.tempest.musiclibrary

import app.cash.tempest.LogicalDb
import app.cash.tempest.reservedwords.ReservedWordsDb
import app.cash.tempest.reservedwords.ReservedWordsItem
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.Projection
import com.amazonaws.services.dynamodbv2.model.ProjectionType
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.aws.dynamodb.testing.InProcessDynamoDbModule
import misk.inject.KAbstractModule

class MusicDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(InProcessDynamoDbModule(
        DynamoDbTable(MusicItem::class) {
          it.apply {
            for (gsi in globalSecondaryIndexes) {
              gsi.withProjection(Projection().withProjectionType(ProjectionType.ALL))
            }
          }
        },
        DynamoDbTable(ReservedWordsItem::class)))
  }

  @Provides
  @Singleton
  fun provideTestMusicDb(amazonDynamoDB: AmazonDynamoDB): MusicDb {
    val dynamoDbMapper = DynamoDBMapper(amazonDynamoDB)
    return LogicalDb(dynamoDbMapper)
  }

  @Provides
  @Singleton
  fun provideTestReservedWordsDb(amazonDynamoDB: AmazonDynamoDB): ReservedWordsDb {
    val dynamoDbMapper = DynamoDBMapper(amazonDynamoDB)
    return LogicalDb(dynamoDbMapper)
  }
}
