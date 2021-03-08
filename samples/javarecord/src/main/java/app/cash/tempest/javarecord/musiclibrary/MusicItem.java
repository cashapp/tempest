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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@DynamoDBTable(tableName = "j_music_items")
public class MusicItem {
  // All Items.
  @DynamoDBHashKey
  @DynamoDBIndexRangeKey(globalSecondaryIndexNames = {"genre_album_index", "artist_album_index"})
  String partition_key = null;
  @DynamoDBRangeKey
  String sort_key = null;

  // AlbumInfo.
  @DynamoDBAttribute
  String album_title = null;
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "artist_album_index")
  @DynamoDBAttribute
  String artist_name = null;
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = LocalDateTypeConverter.class)
  LocalDate release_date = null;
  @DynamoDBAttribute
  @DynamoDBIndexHashKey(globalSecondaryIndexName = "genre_album_index")
  String genre_name = null;

  // AlbumTrack.
  @DynamoDBAttribute
  @DynamoDBIndexRangeKey(localSecondaryIndexName = "album_track_title_index")
  String track_title = null;
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = DurationTypeConverter.class)
  Duration run_length = null;

  // PlaylistInfo.
  @DynamoDBAttribute
  String playlist_name = null;
  @DynamoDBAttribute
  Integer playlist_size = null;
  @DynamoDBAttribute
  @DynamoDBTypeConverted(converter = AlbumTrackKeyListTypeConverter.class)
  List<AlbumTrack.Key> playlist_tracks = null;
  @DynamoDBAttribute
  Long playlist_version = null;

  // PlaylistEntry.
  @DynamoDBAttribute
  String track_token = null;
}

class DurationTypeConverter implements DynamoDBTypeConverter<String, Duration> {

  @Override public String convert(Duration duration) {
    return duration.toString();
  }

  @Override public Duration unconvert(String duration) {
    return Duration.parse(duration);
  }
}

class LocalDateTypeConverter implements DynamoDBTypeConverter<String, LocalDate> {

  @Override public String convert(LocalDate localDate) {
    return localDate.toString();
  }

  @Override public LocalDate unconvert(String localDate) {
    return LocalDate.parse(localDate);
  }
}

class AlbumTrackKeyListTypeConverter
    implements DynamoDBTypeConverter<AttributeValue, List<AlbumTrack.Key>> {

  @Override public AttributeValue convert(List<AlbumTrack.Key> keys) {
    return new AttributeValue().withL(
        keys.stream()
            .map(it -> new AttributeValue().withS(convert(it)))
            .collect(Collectors.toList())
    );
  }

  @Override public List<AlbumTrack.Key> unconvert(AttributeValue items) {
    return items.getL().stream()
        .map(it -> unconvert(it.getS()))
        .collect(Collectors.toList());
  }

  private AlbumTrack.Key unconvert(String string) {
    var parts = string.split("/");
    return new AlbumTrack.Key(parts[0], parts[1]);
  }

  private String convert(AlbumTrack.Key key) {
    return "${key.album_token}/${key.track_token}";
  }
}
