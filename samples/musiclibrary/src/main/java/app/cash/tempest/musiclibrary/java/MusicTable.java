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

package app.cash.tempest.musiclibrary.java;

import app.cash.tempest.InlineView;
import app.cash.tempest.LogicalTable;
import app.cash.tempest.SecondaryIndex;

public interface MusicTable extends LogicalTable<MusicItem> {
  InlineView<AlbumInfo.Key, AlbumInfo> albumInfo();
  InlineView<AlbumTrack.Key, AlbumTrack> albumTracks();
  InlineView<AlbumInfoOrTrack.Key, AlbumInfoOrTrack> albumInfoOrTracks();

  InlineView<PlaylistInfo.Key, PlaylistInfo> playlistInfo();

  // Global Secondary Indexes.
  SecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo> albumInfoByGenre();
  SecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo> albumInfoByArtist();

  // Local Secondary Indexes.
  SecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack> albumTracksByTitle();
}
