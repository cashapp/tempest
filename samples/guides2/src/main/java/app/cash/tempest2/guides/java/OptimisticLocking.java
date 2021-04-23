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

package app.cash.tempest2.guides.java;

import app.cash.tempest2.musiclibrary.java.PlaylistInfo;
import app.cash.tempest2.musiclibrary.java.MusicTable;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class OptimisticLocking {
  private final MusicTable table;

  public OptimisticLocking(MusicTable table) {
    this.table = table;
  }

  public void changePlaylistName(String playlistToken, String newName) {
    // Read.
    PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
    if (existing == null) {
      throw new IllegalStateException("Playlist does not exist: " + playlistToken);
    }
    // Modify.
    PlaylistInfo newPlaylist = new PlaylistInfo(
        existing.playlist_token,
        newName,
        existing.playlist_tracks,
        // playlist_version.
        existing.playlist_version + 1
    );
    // Write.
    table.playlistInfo().save(
        newPlaylist,
        ifPlaylistVersionIs(existing.playlist_version)
    );
  }

  private Expression ifPlaylistVersionIs(Long playlist_version) {
    return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(Map.of(":playlist_version", AttributeValue.builder().n("" + playlist_version).build()))
        .build();
  }
}
