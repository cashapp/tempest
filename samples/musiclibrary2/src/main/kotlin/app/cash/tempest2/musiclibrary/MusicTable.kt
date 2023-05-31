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

import app.cash.tempest2.Attribute
import app.cash.tempest2.ForIndex
import app.cash.tempest2.InlineView
import app.cash.tempest2.LogicalTable
import app.cash.tempest2.SecondaryIndex
import java.time.Duration
import java.time.LocalDate
import kotlin.text.format
import kotlin.text.toLong

interface MusicTable : LogicalTable<MusicItem> {
  val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>

  val playlistInfo: InlineView<PlaylistInfo.Key, PlaylistInfo>

  // Global Secondary Indexes.
  val albumInfoByGenre: SecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo>
  val albumInfoByArtist: SecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo>
  val albumInfoByLabel: SecondaryIndex<AlbumInfo.LabelIndexOffset, AlbumInfo>

  // Local Secondary Indexes.
  val albumTracksByTitle: SecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack>
}

data class AlbumInfo(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String,
  @Attribute(prefix = "L_", allowEmpty = true)
  val label_name: String? = null
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""

  @Transient
  val key = Key(album_token)

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

  @ForIndex("label_album_index")
  data class LabelIndexOffset(
    val label_name: String,
    val album_token: String? = null,
    // To uniquely identify an item in pagination.
    val sort_key: String? = null
  )
}

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

  @Transient
  val key = Key(album_token, track_token)

  @Transient
  val track_number = track_token.toLong(radix = 16)

  data class Key(
    val album_token: String,
    val track_token: String = ""
  ) {
    constructor(album_token: String, track_number: Long) : this(album_token, "%016x".format(track_number))

    @Transient
    val track_number = if (track_token.isEmpty()) 0 else track_token.toLong(radix = 16)
  }

  @ForIndex("album_track_title_index")
  data class TitleIndexOffset(
    val album_token: String,
    val track_title: String? = null,
    // To uniquely identify an item in pagination.
    val track_token: String? = null
  )
}

data class PlaylistInfo(
  @Attribute(name = "partition_key")
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
