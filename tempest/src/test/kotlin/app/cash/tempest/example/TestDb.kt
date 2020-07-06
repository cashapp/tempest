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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.Projection
import com.amazonaws.services.dynamodbv2.model.ProjectionType
import com.google.inject.Provides
import com.google.inject.Singleton
import java.time.Duration
import java.time.LocalDate
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.inject.KAbstractModule

interface TestDb : LogicalDb {
  val music: MusicTable
}

class MusicDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DockerDynamoDbModule(DynamoDbTable(MusicItem::class) {
      it.apply {
        for (gsi in globalSecondaryIndexes) {
          gsi.withProjection(Projection().withProjectionType(ProjectionType.ALL))
        }
      }
    }))
  }

  @Provides
  @Singleton
  fun provideTestDb(amazonDynamoDB: AmazonDynamoDB): TestDb {
    val dynamoDbMapper = DynamoDBMapper(amazonDynamoDB)
    return LogicalDb(dynamoDbMapper)
  }
}

internal class DurationTypeConverter : DynamoDBTypeConverter<String, Duration> {
  override fun unconvert(string: String): Duration {
    return Duration.parse(string)
  }

  override fun convert(duration: Duration): String {
    return duration.toString()
  }
}

internal class LocalDateTypeConverter : DynamoDBTypeConverter<String, LocalDate> {
  override fun unconvert(string: String): LocalDate {
    return LocalDate.parse(string)
  }

  override fun convert(localDate: LocalDate): String {
    return localDate.toString()
  }
}

internal class AlbumTrackKeyListTypeConverter :
  DynamoDBTypeConverter<AttributeValue, List<AlbumTrack.Key>> {
  override fun unconvert(items: AttributeValue): List<AlbumTrack.Key> {
    return items.l.map { unconvert(it.s) }
  }

  override fun convert(keys: List<AlbumTrack.Key>): AttributeValue {
    return AttributeValue().withL(keys.map { AttributeValue().withS(convert(it)) })
  }

  private fun unconvert(string: String): AlbumTrack.Key {
    val parts = string.split("/")
    return AlbumTrack.Key(parts[0], parts[1])
  }

  private fun convert(key: AlbumTrack.Key): String {
    return "${key.album_token}/${key.track_token}"
  }
}
