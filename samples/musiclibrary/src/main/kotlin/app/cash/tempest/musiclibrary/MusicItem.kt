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

package app.cash.tempest.musiclibrary

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.time.Duration
import java.time.LocalDate

@DynamoDBTable(tableName = "music_items")
class MusicItem {
  // All Items.
  @DynamoDBHashKey
  @DynamoDBIndexRangeKey(globalSecondaryIndexNames = ["genre_album_index", "artist_album_index"])
  var partition_key: String? = null
  @DynamoDBRangeKey
  var sort_key: String? = null

  // AlbumInfo.
  @DynamoDBAttribute
  var album_title: String? = null
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "artist_album_index")
  @DynamoDBAttribute
  var artist_name: String? = null
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = LocalDateTypeConverter::class)
  var release_date: LocalDate? = null
  @DynamoDBAttribute
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "genre_album_index")
  var genre_name: String? = null

  // AlbumTrack.
  @DynamoDBAttribute
  @DynamoDBIndexRangeKey(localSecondaryIndexName = "album_track_title_index")
  var track_title: String? = null
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = DurationTypeConverter::class)
  var run_length: Duration? = null

  // PlaylistInfo.
  @DynamoDBAttribute
  var playlist_name: String? = null
  @DynamoDBAttribute
  var playlist_size: Int? = null
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = AlbumTrackKeyListTypeConverter::class)
  var playlist_tracks: List<AlbumTrack.Key>? = null
  @DynamoDBAttribute
  var playlist_version: Long? = null

  // PlaylistEntry.
  @DynamoDBAttribute
  var track_token: String? = null
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
