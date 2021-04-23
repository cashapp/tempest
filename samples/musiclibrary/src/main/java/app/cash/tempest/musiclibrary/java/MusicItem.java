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

import app.cash.tempest.musiclibrary.AlbumTrackKeyListTypeConverter;
import app.cash.tempest.musiclibrary.DurationTypeConverter;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@DynamoDBTable(tableName = "j_music_items")
public class MusicItem {
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

  @DynamoDBHashKey(attributeName = "partition_key")
  @DynamoDBIndexRangeKey(globalSecondaryIndexNames = {"genre_album_index", "artist_album_index"})
  public String getPartitionKey() {
    return partition_key;
  }

  public void setPartitionKey(String partition_key) {
    this.partition_key = partition_key;
  }

  @DynamoDBRangeKey(attributeName = "sort_key")
  public String getSortKey() {
    return sort_key;
  }

  public void setSortKey(String sort_key) {
    this.sort_key = sort_key;
  }

  @DynamoDBAttribute(attributeName = "album_title")
  public String getAlbumTitle() {
    return album_title;
  }

  public void setAlbumTitle(String album_title) {
    this.album_title = album_title;
  }

  @DynamoDBAttribute(attributeName = "artist_name")
  @DynamoDBIndexHashKey(globalSecondaryIndexNames = "artist_album_index")
  public String getArtistName() {
    return artist_name;
  }

  public void setArtistName(String artist_name) {
    this.artist_name = artist_name;
  }

  @DynamoDBAttribute(attributeName = "release_date")
  @DynamoDBTypeConverted(converter = LocalDateTypeConverter.class)
  public LocalDate getReleaseDate() {
    return release_date;
  }

  public void setReleaseDate(LocalDate release_date) {
    this.release_date = release_date;
  }

  @DynamoDBAttribute(attributeName = "genre_name")
  @DynamoDBIndexHashKey(globalSecondaryIndexNames = "genre_album_index")
  public String getGenreName() {
    return genre_name;
  }

  public void setGenreName(String genre_name) {
    this.genre_name = genre_name;
  }

  @DynamoDBAttribute(attributeName = "track_title")
  @DynamoDBIndexRangeKey(localSecondaryIndexName = "album_track_title_index")
  public String getTrackTitle() {
    return track_title;
  }

  public void setTrackTitle(String track_title) {
    this.track_title = track_title;
  }

  @DynamoDBAttribute(attributeName = "run_length")
  @DynamoDBTypeConverted(converter = DurationTypeConverter.class)
  public Duration getRunLength() {
    return run_length;
  }

  public void setRunLength(Duration run_length) {
    this.run_length = run_length;
  }

  @DynamoDBAttribute(attributeName = "playlist_name")
  public String getPlaylistName() {
    return playlist_name;
  }

  public void setPlaylistName(String playlist_name) {
    this.playlist_name = playlist_name;
  }

  @DynamoDBAttribute(attributeName = "playlist_size")
  public Integer getPlaylistSize() {
    return playlist_size;
  }

  public void setPlaylistSize(Integer playlist_size) {
    this.playlist_size = playlist_size;
  }

  @DynamoDBAttribute(attributeName = "playlist_tracks")
  @DynamoDBTypeConverted(converter = AlbumTrackKeyListTypeConverter.class)
  public List<AlbumTrack.Key> getPlaylistTracks() {
    return playlist_tracks;
  }

  public void setPlaylistTracks(
      List<AlbumTrack.Key> playlist_tracks) {
    this.playlist_tracks = playlist_tracks;
  }

  @DynamoDBAttribute(attributeName = "playlist_version")
  public Long getPlaylistVersion() {
    return playlist_version;
  }

  public void setPlaylistVersion(Long playlist_version) {
    this.playlist_version = playlist_version;
  }

  @DynamoDBAttribute(attributeName = "track_token")
  public String getTrackToken() {
    return track_token;
  }

  public void setTrackToken(String track_token) {
    this.track_token = track_token;
  }

  public static class LocalDateTypeConverter implements DynamoDBTypeConverter<String, LocalDate> {

    @Override public String convert(LocalDate object) {
      return object.toString();
    }

    @Override public LocalDate unconvert(String object) {
      return LocalDate.parse(object);
    }
  }
}
