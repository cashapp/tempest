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

package app.cash.tempest2.musiclibrary.java;

import app.cash.tempest2.Attribute;
import java.util.List;

public class PlaylistInfo {
  @Attribute(name = "partition_key")
  public final String playlist_token;
  public final String playlist_name;
  public final List<AlbumTrack.Key> playlist_tracks;
  public final Long playlist_version;
  @Attribute(prefix = "INFO_")
  public final String sort_key = "";

  public PlaylistInfo(String playlist_token, String playlist_name,
      List<AlbumTrack.Key> playlist_tracks) {
    this(playlist_token, playlist_name, playlist_tracks, 1L);
  }

  public PlaylistInfo(String playlist_token, String playlist_name,
      List<AlbumTrack.Key> playlist_tracks, Long playlist_version) {
    this.playlist_token = playlist_token;
    this.playlist_name = playlist_name;
    this.playlist_tracks = playlist_tracks;
    this.playlist_version = playlist_version;
  }

  public static class Key {
    public final String playlist_token;
    public final String sort_key = "";

    public Key(String playlist_token) {
      this.playlist_token = playlist_token;
    }
  }
}
