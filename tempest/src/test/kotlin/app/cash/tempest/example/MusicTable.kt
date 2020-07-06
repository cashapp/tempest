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

import app.cash.tempest.Attribute
import app.cash.tempest.ForIndex
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import app.cash.tempest.SecondaryIndex
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted
import java.time.Duration
import java.time.LocalDate

interface MusicTable : LogicalTable<MusicItem> {
  val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>

  val playlistInfo: InlineView<PlaylistInfo.Key, PlaylistInfo>

  // Global Secondary Indexes.
  val albumInfoByGenre: SecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo>
  val albumInfoByArtist: SecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo>

  // Local Secondary Indexes.
  val albumTracksByTitle: SecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack>
}

data class AlbumInfo(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""

  data class Key(
    val album_token: String
  ) {
    val sort_key: String = ""
  }

  @ForIndex("genre_album_index")
  data class GenreIndexOffset(
    val genre_name: String,
    val album_token: String? = null,
    // To uniquely identify an item in pagination.
    val sort_key: String? = null
  )

  @ForIndex("artist_album_index")
  data class ArtistIndexOffset(
    val artist_name: String,
    val album_token: String? = null,
    // To uniquely identify an item in pagination.
    val sort_key: String? = null
  )
}

val AlbumInfo.key: AlbumInfo.Key
  get() = AlbumInfo.Key(album_token)

data class AlbumTrack(
  @Attribute(name = "partition_key")
  val album_token: String,
  @Attribute(name = "sort_key", prefix = "TRACK_")
  val track_token: String,
  val track_title: String,
  val run_length: Duration
) {
  constructor(
    album_token: String,
    track_number: Long,
    track_title: String,
    run_length: Duration
  ) : this(album_token, "%016x".format(track_number), track_title, run_length)

  data class Key(
    val album_token: String,
    val track_token: String = ""
  ) {
    constructor(album_token: String, track_number: Long) : this(album_token, "%016x".format(track_number))
  }

  @ForIndex("album_track_title_index")
  data class TitleIndexOffset(
    val album_token: String,
    val track_title: String,
    // To uniquely identify an item in pagination.
    val track_token: String? = null
  )
}

val AlbumTrack.Key.track_number: Long
  get() = track_token.toLong(radix = 16)

val AlbumTrack.key: AlbumTrack.Key
  get() = AlbumTrack.Key(album_token, album_token)

val AlbumTrack.track_number: Long
  get() = track_token.toLong(radix = 16)

data class PlaylistInfo(
  @Attribute(name = "partition_key") // TODO prefix?
  val playlist_token: String,
  val playlist_name: String,
  val playlist_tracks: List<AlbumTrack.Key>,
  val playlist_version: Long = 1
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""

  data class Key(
    val playlist_token: String
  ) {
    val sort_key: String = ""
  }
}

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
