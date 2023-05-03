# Tempest
Typesafe DynamoDB for Kotlin and Java.

See the [project website](https://cashapp.github.io/tempest) for documentation and APIs.

## Efficient DynamoDB

DynamoDB applications perform best (and cost the least to operate!) when data is organized for locality:

* **Multiple types per table**: The application can store different entity types in a single table. DynamoDB schemas are flexible.
* **Related entities are stored together**: Entities that are accessed together should be stored together. This makes it possible to answer common queries in as few requests as possible, [ideally one](https://www.alexdebrie.com/posts/dynamodb-single-table/#the-solution-pre-join-your-data-into-item-collections).

### Example

Let's build a music library with the following features:

* Fetching multiple albums, each of which contains multiple tracks.
* Fetching individual tracks.

We express it like this in code:

#### Kotlin

```kotlin
interface MusicLibrary {
  fun getAlbum(key: AlbumKey): Album
  fun getTrack(key: TrackKey): Track
}

data class Album(
  val album_title: String,
  val album_artist: String,
  val release_date: String,
  val genre: String,
  val tracks: List<Track>
)

data class Track(
  val track_title: String,
  val run_length: String
)
```

#### Java

```java
public interface MusicLibrary {
  Album getAlbum(AlbumKey key);
  Track getTrack(TrackKey key); 
}

public class Album {
  public final String album_title;
  public final String album_artist;
  public final String release_date;
  public final String genre;
  public final List<Track> tracks; 
}

public class Track(
  public final String track_title;
  public final String run_length;
)
```

We optimize for this access pattern by putting albums and tracks in the same table:

<table>
  <tbody>
    <tr>
      <td colspan=2 align="center">Primary Key</td>
      <td rowspan=2 colspan=4 align="center" valign="middle">Attributes</td>
    </tr>
    <tr>
      <td><strong>partition_key</strong></td>
      <td><strong>sort_key</strong></td>
    </tr>
    <tr>
      <!-- Note: It is important to declare both vertical-align and valign here. 
           vertical-align only works in the project website 
           while valign only works in Github's formatting for README.md. -->
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_1</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">INFO</td>
      <td><strong>album_title</strong></td>
      <td><strong>album_artist</strong></td>
      <td><strong>release_date</strong></td>
      <td><strong>genre</strong></td>
    </tr>
    <tr>
      <td>The Dark Side of the Moon</td>
      <td>Pink Floyd</td>
      <td>1973-03-01</td>
      <td>Progressive rock</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_1</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">TRACK_1</td>
      <td><strong>track_title</strong></td>
      <td><strong>run_length</strong></td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td>Speak to Me</td>
      <td>PT1M13S</td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_1</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">TRACK_2</td>
      <td><strong>track_title</strong></td>
      <td><strong>run_length</strong></td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td>Breathe</td>
      <td>PT2M43S</td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_1</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">TRACK_3</td>
      <td><strong>track_title</strong></td>
      <td><strong>run_length</strong></td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td>On the Run</td>
      <td>PT3M36S</td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td colspan=6>...</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_2</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">INFO</td>
      <td><strong>album_title</strong></td>
      <td><strong>album_artist</strong></td>
      <td><strong>release_date</strong></td>
      <td><strong>genre</strong></td>
    </tr>
    <tr>
      <td>The Wall</td>
      <td>Pink Floyd</td>
      <td>1979-11-30</td>
      <td>Progressive rock</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_2</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">TRACK_1</td>
      <td><strong>track_title</strong></td>
      <td><strong>run_length</strong></td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td>In the Flesh?</td>
      <td>PT3M20S</td>
      <td>&nbsp;</td>
      <td>&nbsp;</td>
    </tr>
    <tr>
      <td colspan=6>...</td>
    </tr>
  </tbody>
</table>

This table uses a [composite primary key](https://aws.amazon.com/blogs/database/choosing-the-right-dynamodb-partition-key/), `(parition_key, sort_key)`, to identify each item.

* The key `("ALBUM_1", "INFO")` identifies `ALBUM_1`'s metadata.
* The key `("ALBUM_1", "TRACK_1")` identifies `ALBUM_1`'s first track.

This table stores tracks belonging to the same album together and sorts them by the track number. The application needs only one request to DynamoDB to get the album and its tracks.

```
aws dynamodb query \
    --table-name music_library_items \
    --key-conditions '{ 
        "PK": { 
            "ComparisonOperator": "EQ",
            "AttributeValueList": [ { "S": "ALBUM_1" } ]
        } 
    }'
```

## Why Tempest?

For locality, we smashed together several entity types in the same table. This improves performance! But it breaks type safety in DynamoDBMapper.

### DynamoDBMapper API

[`DynamoDBMapper`](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.html) / [`DynamoDbEnhancedClient`](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/enhanced/dynamodb/DynamoDbEnhancedClient.html), the official Java API, forces you to write weakly-typed code that models the actual persistence type.

#### Kotlin

```kotlin
// NOTE: This is not Tempest! It is an example used for comparison.
@DynamoDBTable(tableName = "music_library_items")
class MusicLibraryItem {
  // All Items.
  @DynamoDBHashKey
  var partition_key: String? = null
  @DynamoDBRangeKey
  var sort_key: String? = null

  // AlbumInfo.
  @DynamoDBAttribute
  var album_title: String? = null
  @DynamoDBAttribute
  var album_artist: String? = null
  @DynamoDBAttribute
  var release_date: String? = null
  @DynamoDBAttribute
  var genre: String? = null

  // AlbumTrack.
  @DynamoDBAttribute
  var track_title: String? = null
  @DynamoDBAttribute
  var run_length: String? = null
}
```

#### Java

```java
// NOTE: This is not Tempest! It is an example used for comparison.
@DynamoDBTable(tableName = "music_library_items")
public class MusicLibraryItem {
  // All Items.
  String partition_key = null;
  String sort_key = null;

  // AlbumInfo.
  String album_title = null;
  String artist_name = null;
  String release_date = null;
  String genre_name = null;

  // AlbumTrack.
  String track_title = null;
  String run_length = null;

  @DynamoDBHashKey(attributeName = "partition_key")
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
  public String getArtistName() {
    return artist_name;
  }

  public void setArtistName(String artist_name) {
    this.artist_name = artist_name;
  }

  @DynamoDBAttribute(attributeName = "release_date")
  public String getReleaseDate() {
    return release_date;
  }

  public void setReleaseDate(String release_date) {
    this.release_date = release_date;
  }

  @DynamoDBAttribute(attributeName = "genre_name")
  public String getGenreName() {
    return genre_name;
  }

  public void setGenreName(String genre_name) {
    this.genre_name = genre_name;
  }

  @DynamoDBAttribute(attributeName = "track_title")
  public String getTrackTitle() {
    return track_title;
  }

  public void setTrackTitle(String track_title) {
    this.track_title = track_title;
  }

  @DynamoDBAttribute(attributeName = "run_length")
  public String getRunLength() {
    return run_length;
  }

  public void setRunLength(String run_length) {
    this.run_length = run_length;
  }
}
```

Note that `MusicLibraryItem` is a union type of all the entity types: `AlbumInfo` and `AlbumTrack`. Because all of its attributes are nullable and mutable, code that interacts with it is brittle and error prone.

### Tempest API

Tempest restores maintainability without losing locality. It lets you declare strongly-typed key and item classes for each logical type in the domain layer.

#### Kotlin
    
```kotlin
data class AlbumInfo(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val album_artist: String,
  val release_date: String,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""

  data class Key(
    val album_token: String
  ) {
    val sort_key: String = ""
  }
}

data class AlbumTrack(
  @Attribute(name = "partition_key")
  val album_token: String,
  @Attribute(name = "sort_key", prefix = "TRACK_")
  val track_token: String,
  val track_title: String,
  val run_length: String
) {
  data class Key(
    val album_token: String,
    val track_token: String
  )
}
```

#### Java

```java
public class AlbumInfo {
  @Attribute(name = "partition_key")
  public final String album_token;
  public final String album_title;
  public final String artist_name;
  public final String release_date;
  public final String genre_name;

  @Attribute(prefix = "INFO_")
  public final String sort_key = "";

  public static class Key {
    public final String album_token;
    public final String sort_key = "";
  }
}

public class AlbumTrack {
  @Attribute(name = "partition_key")
  public final String album_token;
  @Attribute(name = "sort_key", prefix = "TRACK_")
  public final String track_token;
  public final String track_title;
  public final String run_length;

  public static class Key {
    public final String album_token;
    public final String track_token;
  }
}
```

You build business logic with logical types. Tempest handles mapping them to the underlying persistence type.

#### Kotlin

```kotlin
interface MusicLibraryTable : LogicalTable<MusicLibraryItem> {
  val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
}

private val musicLibrary: MusicLibraryTable

// Load.
fun getAlbumTitle(albumToken: String): String? {
  val key = AlbumInfo.Key(albumToken)
  val albumInfo = musicLibrary.albumInfo.load(key) ?: return null
  return albumInfo.album_title
}

// Update.
fun addAlbumTrack(
  albumToken: String, 
  track_token: String, 
  track_title: String, 
  run_length: String
) {
  val newAlbumTrack = AlbumTrack(albumToken, track_token, track_title, run_length)
  musicLibrary.albumTracks.save(newAlbumTrack)
} 

// Query.
fun getAlbumTrackTitles(albumToken: String): List<String> {
  val page = musicLibrary.albumTracks.query(
    keyCondition = BeginsWith(AlbumTrack.Key(albumToken))
  )
  return page.contents.map { it.track_title }
}
```

#### Java

```java
public interface MusicLibraryTable extends LogicalTable<MusicLibraryItem> {
  InlineView<AlbumInfo.Key, AlbumInfo> albumInfo();
  InlineView<AlbumTrack.Key, AlbumTrack> albumTracks();
}

private MusicLibraryTable musicLibrary; 

// Load.
@Nullable
public String getAlbumTitle(String albumToken) {
  AlbumInfo albumInfo = table.albumInfo().load(new AlbumInfo.Key(albumToken));
  if (albumInfo == null) {
    return null;
  }
  return albumInfo.album_title;
}

// Update.
public void addAlbumTrack(
  String albumToken, 
  String track_token, 
  String track_title, 
  String run_length
) {
  AlbumTrack newAlbumTrack = new AlbumTrack(albumToken, track_token, track_title, run_length);
  musicLibrary.albumTracks().save(newAlbumTrack);
}

// Query.
public List<String> getAlbumTrackTitles(String albumToken) {
  Page<AlbumTrack.Key, AlbumTrack> page = musicLibrary.albumTracks().query(
      // keyCondition.
      new BeginsWith<>(
          // prefix.
          new AlbumTrack.Key(albumToken)
      )
  );
  return page.getContents().stream().map(track -> track.track_title).collect(Collectors.toList());
}
```

## Get Tempest

For AWS SDK 1.x:

```groovy
implementation "app.cash.tempest:tempest:1.7.0"
```

For AWS SDK 2.x:

```groovy
implementation "app.cash.tempest:tempest2:1.7.0"
```

## Migrating From Tempest 1 to Tempest 2

Please follow the [Migration Guide](docs/guide/v2_upgrade_guide.md) that has been set up to upgrade from Tempest 1 (AWS SDK 1.x) to Tempest 2 (AWS SDK 2.x)

## License

    Copyright 2020 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
