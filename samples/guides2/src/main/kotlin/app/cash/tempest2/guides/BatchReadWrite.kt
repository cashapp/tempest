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

package app.cash.tempest2.guides

import app.cash.tempest2.BatchWriteSet
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.PlaylistInfo

class BatchReadWrite(
  private val db: MusicDb
) {

  // Batch Load.
  fun loadPlaylistTracks(playlist: PlaylistInfo): List<AlbumTrack> {
    val results = db.batchLoad(
      keys = playlist.playlist_tracks, // [AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ...]
      consistentReads = false,
    )
    return results.getItems<AlbumTrack>()
  }

  // Batch Write.
  fun backfill(
    albumTracksToSave: List<AlbumTrack>,
    albumTracksToDelete: List<AlbumTrack.Key>
  ): Boolean {
    val writeSet = BatchWriteSet.Builder()
      .clobber(albumTracksToSave)
      .delete(albumTracksToDelete)
      .build()
    val result = db.batchWrite(writeSet)
    return result.isSuccessful
  }
}
