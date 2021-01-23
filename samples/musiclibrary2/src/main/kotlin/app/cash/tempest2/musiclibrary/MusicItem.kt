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

package app.cash.tempest2.musiclibrary

import java.time.Duration
import java.time.LocalDate
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@DynamoDbBean
class MusicItem {
  // All Items.
  @get:DynamoDbPartitionKey
  @get:DynamoDbSecondarySortKey(indexNames = ["genre_album_index", "artist_album_index"])
  var partition_key: String? = null
  @get:DynamoDbSortKey
  var sort_key: String? = null

  // AlbumInfo.
  var album_title: String? = null
  @get:DynamoDbSecondaryPartitionKey(indexNames = ["artist_album_index"])
  var artist_name: String? = null
  @get:DynamoDbConvertedBy(LocalDateTypeConverter::class)
  var release_date: LocalDate? = null
  @get:DynamoDbSecondaryPartitionKey(indexNames = ["genre_album_index"])
  var genre_name: String? = null

  // AlbumTrack.
  @get:DynamoDbSecondarySortKey(indexNames = ["album_track_title_index"])
  var track_title: String? = null
  @get:DynamoDbConvertedBy(DurationTypeConverter::class)
  var run_length: Duration? = null

  // PlaylistInfo.
  var playlist_name: String? = null
  var playlist_size: Int? = null
  @get:DynamoDbConvertedBy(AlbumTrackKeyListTypeConverter::class)
  var playlist_tracks: List<AlbumTrack.Key>? = null
  var playlist_version: Long? = null

  // PlaylistEntry.
  var track_token: String? = null
}

internal class DurationTypeConverter : AttributeConverter<Duration> {

  override fun transformFrom(input: Duration): AttributeValue {
    return AttributeValue.builder().s(input.toString()).build()
  }

  override fun transformTo(input: AttributeValue): Duration {
    return Duration.parse(input.s())
  }

  override fun type(): EnhancedType<Duration> {
    return EnhancedType.of(Duration::class.java)
  }

  override fun attributeValueType(): AttributeValueType {
    return AttributeValueType.S
  }
}

internal class LocalDateTypeConverter : AttributeConverter<LocalDate> {

  override fun transformFrom(input: LocalDate): AttributeValue {
    return AttributeValue.builder().s(input.toString()).build()
  }

  override fun transformTo(input: AttributeValue): LocalDate {
    return LocalDate.parse(input.s())
  }

  override fun type(): EnhancedType<LocalDate> {
    return EnhancedType.of(LocalDate::class.java)
  }

  override fun attributeValueType(): AttributeValueType {
    return AttributeValueType.S
  }
}

internal class AlbumTrackKeyListTypeConverter :
  AttributeConverter<List<AlbumTrack.Key>> {

  override fun transformFrom(input: List<AlbumTrack.Key>): AttributeValue {
    return AttributeValue.builder()
      .l(input.map { AttributeValue.builder().s(convert(it)).build() })
      .build()
  }

  override fun transformTo(input: AttributeValue): List<AlbumTrack.Key> {
    return input.l().map { unconvert(it.s()) }
  }

  override fun type(): EnhancedType<List<AlbumTrack.Key>> {
    return EnhancedType.listOf(AlbumTrack.Key::class.java)
  }

  override fun attributeValueType(): AttributeValueType {
    return AttributeValueType.L
  }

  private fun unconvert(string: String): AlbumTrack.Key {
    val parts = string.split("/")
    return AlbumTrack.Key(parts[0], parts[1])
  }

  private fun convert(key: AlbumTrack.Key): String {
    return "${key.album_token}/${key.track_token}"
  }
}
