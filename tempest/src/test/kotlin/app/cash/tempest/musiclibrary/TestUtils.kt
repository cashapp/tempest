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

import app.cash.tempest.Page
import java.time.Duration
import java.time.LocalDate

data class Album(
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String,
  val tracks: List<Track>
) {
  val trackTitles = tracks.map { it.track_title }
}

data class Track(val track_title: String, val run_length: Duration)

val Page<*, AlbumTrack>.trackTitles: List<String>
  get() = contents.map { it.track_title }

val Page<*, AlbumInfo>.albumTitles: List<String>
  get() = contents.map { it.album_title }

fun MusicTable.givenAlbums(vararg albums: Album) {
  for (album in albums) {
    albumInfo.save(
      AlbumInfo(
        album.album_token,
        album.album_title,
        album.artist_name,
        album.release_date,
        album.genre_name
      )
    )
    for ((i, track) in album.tracks.withIndex()) {
      albumTracks.save(
        AlbumTrack(
          album.album_token,
          i + 1L,
          track.track_title,
          track.run_length
        )
      )
    }
  }
}
