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

package app.cash.tempest.javarecord.musiclibrary;

import app.cash.tempest.Attribute;
import app.cash.tempest.ForIndex;
import java.time.LocalDate;
import javax.annotation.Nullable;

public record AlbumInfo(
    @Attribute(name = "partition_key")
    String album_token,
    String album_title,
    String artist_name,
    LocalDate release_date,
    String genre_name,
    @Attribute(prefix = "INFO_")
    String sort_key
) {

  public AlbumInfo(
      String album_token,
      String album_title,
      String artist_name,
      LocalDate release_date,
      String genre_name) {
    this(album_token,
        album_title,
        artist_name,
        release_date,
        genre_name,
        "");
  }

  public Key key() {
    return new Key(album_token);
  }

  public static record Key(
      String album_token,
      String sort_key
  ) {
    public Key(String album_token) {
      this(album_token, "");
    }
  }

  @ForIndex(name = "genre_album_index")
  public static record GenreIndexOffset(
      String genre_name,
      @Nullable
      String album_token,
      // To uniquely identify an item in pagination.
      @Nullable
      String sort_key
  ) {

    public GenreIndexOffset(String genre_name) {
      this(genre_name, null, null);
    }

    public GenreIndexOffset(String genre_name, String album_token) {
      this(genre_name, album_token, null);
    }
  }

  @ForIndex(name = "artist_album_index")
  public static record ArtistIndexOffset(
      String artist_name,
      @Nullable
      String album_token,
      // To uniquely identify an item in pagination.
      @Nullable
      String sort_key
  ) {

    public ArtistIndexOffset(String artist_name) {
      this(artist_name, null, null);
    }

    public ArtistIndexOffset(String artist_name, String album_token) {
      this(artist_name, album_token, null);
    }
  }
}
