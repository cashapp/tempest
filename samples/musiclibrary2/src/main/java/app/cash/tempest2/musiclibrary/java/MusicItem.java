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

import app.cash.tempest2.musiclibrary.AlbumTrackKeyListTypeConverter;
import app.cash.tempest2.musiclibrary.DurationTypeConverter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@DynamoDbBean
public class MusicItem {

  public static String TABLE_NAME = "j_music_items";

  // All Items.
  String partition_key = null;
  String sort_key = null;

  // AlbumInfo.
  String album_title = null;
  String artist_name = null;
  LocalDate release_date = null;
  String genre_name = null;

  // AlbumTrack.
  String track_title = null;
  Duration run_length = null;

  // PlaylistInfo.
  String playlist_name = null;
  Integer playlist_size = null;
  List<AlbumTrack.Key> playlist_tracks = null;
  Long playlist_version = null;

  // PlaylistEntry.
  String track_token = null;

  @DynamoDbAttribute("partition_key")
  @DynamoDbPartitionKey
  @DynamoDbSecondarySortKey(indexNames = {"genre_album_index", "artist_album_index"})
  public String getPartitionKey() {
    return partition_key;
  }

  public void setPartitionKey(String partition_key) {
    this.partition_key = partition_key;
  }

  @DynamoDbAttribute("sort_key")
  @DynamoDbSortKey
  public String getSortKey() {
    return sort_key;
  }

  public void setSortKey(String sort_key) {
    this.sort_key = sort_key;
  }

  @DynamoDbAttribute("album_title")
  public String getAlbumTitle() {
    return album_title;
  }

  public void setAlbumTitle(String album_title) {
    this.album_title = album_title;
  }

  @DynamoDbAttribute("artist_name")
  @DynamoDbSecondaryPartitionKey(indexNames = "artist_album_index")
  public String getArtistName() {
    return artist_name;
  }

  public void setArtistName(String artist_name) {
    this.artist_name = artist_name;
  }

  @DynamoDbAttribute("release_date")
  @DynamoDbConvertedBy(LocalDateTypeConverter.class)
  public LocalDate getReleaseDate() {
    return release_date;
  }

  public void setReleaseDate(LocalDate release_date) {
    this.release_date = release_date;
  }

  @DynamoDbAttribute("genre_name")
  @DynamoDbSecondaryPartitionKey(indexNames = "genre_album_index")
  public String getGenreName() {
    return genre_name;
  }

  public void setGenreName(String genre_name) {
    this.genre_name = genre_name;
  }

  @DynamoDbAttribute("track_title")
  @DynamoDbSecondarySortKey(indexNames = "album_track_title_index")
  public String getTrackTitle() {
    return track_title;
  }

  public void setTrackTitle(String track_title) {
    this.track_title = track_title;
  }

  @DynamoDbAttribute("run_length")
  @DynamoDbConvertedBy(DurationTypeConverter.class)
  public Duration getRunLength() {
    return run_length;
  }

  public void setRunLength(Duration run_length) {
    this.run_length = run_length;
  }

  @DynamoDbAttribute("playlist_name")
  public String getPlaylistName() {
    return playlist_name;
  }

  public void setPlaylistName(String playlist_name) {
    this.playlist_name = playlist_name;
  }

  @DynamoDbAttribute("playlist_size")
  public Integer getPlaylistSize() {
    return playlist_size;
  }

  public void setPlaylistSize(Integer playlist_size) {
    this.playlist_size = playlist_size;
  }

  @DynamoDbAttribute("playlist_tracks")
  @DynamoDbConvertedBy(AlbumTrackKeyListTypeConverter.class)
  public List<AlbumTrack.Key> getPlaylistTracks() {
    return playlist_tracks;
  }

  public void setPlaylistTracks(
      List<AlbumTrack.Key> playlist_tracks) {
    this.playlist_tracks = playlist_tracks;
  }

  @DynamoDbAttribute("playlist_version")
  public Long getPlaylistVersion() {
    return playlist_version;
  }

  public void setPlaylistVersion(Long playlist_version) {
    this.playlist_version = playlist_version;
  }

  @DynamoDbAttribute("track_token")
  public String getTrackToken() {
    return track_token;
  }

  public void setTrackToken(String track_token) {
    this.track_token = track_token;
  }

  public static class LocalDateTypeConverter implements AttributeConverter<LocalDate> {

    @Override public AttributeValue transformFrom(LocalDate input) {
      return AttributeValue.builder().s(input.toString()).build();
    }

    @Override public LocalDate transformTo(AttributeValue input) {
      return LocalDate.parse(input.s());
    }

    @Override public EnhancedType<LocalDate> type() {
      return EnhancedType.of(LocalDate.class);
    }

    @Override public AttributeValueType attributeValueType() {
      return AttributeValueType.S;
    }
  }
}
