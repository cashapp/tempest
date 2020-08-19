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

package app.cash.tempest.musiclibrary.java;

import app.cash.tempest.Attribute;
import app.cash.tempest.ForIndex;
import java.time.Duration;
import javax.annotation.Nullable;

public class AlbumTrack {
  @Attribute(name = "partition_key")
  public final String album_token;
  @Attribute(name = "sort_key", prefix = "TRACK_")
  public final String track_token;
  public final String track_title;
  public final Duration run_length;
  public final transient Key key;
  public final transient Long track_number;

  public AlbumTrack(
      String album_token,
      String track_token,
      String track_title,
      Duration run_length) {
    this.album_token = album_token;
    this.track_token = track_token;
    this.track_title = track_title;
    this.run_length = run_length;
    key = new Key(album_token, track_token);
    track_number = Long.parseLong(track_token, 16);
  }

  public AlbumTrack(
      String album_token,
      Long track_number,
      String track_title,
      Duration run_length) {
    this(album_token, String.format("%016x", track_number), track_title, run_length);
  }

  public static class Key {
    public final String album_token;
    public final String track_token;
    public final transient Long track_number;

    public Key(String album_token, String track_token) {
      this.album_token = album_token;
      this.track_token = track_token;
      track_number = Long.parseLong(track_token, 16);
    }

    public Key(String album_token, Long track_number) {
      this(album_token, String.format("%016x", track_number));
    }

    public Key(String album_token) {
      this(album_token, "");
    }
  }

  @ForIndex(name = "album_track_title_index")
  public static class TitleIndexOffset {
    public final String album_token;
    public final String track_title;
    // To uniquely identify an item in pagination.
    @Nullable
    public final String track_token;

    public TitleIndexOffset(String album_token, String track_title) {
      this(album_token, track_title, null);
    }

    public TitleIndexOffset(String album_token, String track_title, String track_token) {
      this.album_token = album_token;
      this.track_title = track_title;
      this.track_token = track_token;
    }
  }
}
