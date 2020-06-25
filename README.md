# Tempest
Typesafe DynamoDB in Kotlin

See the [project website](https://cashapp.github.io/tempest) for documentation and APIs.

## DynamoDB

A well-designed DynamoDB application maximizes data locality of the most common queries.

This typically means:

* The application stores all logical entity types in the same table. This is possible because DynamoDB schema is flexible.
* The application keeps related logical entities together in one place to answer the common queries with as few requests to DynamoDB as possible, [ideally one](https://www.alexdebrie.com/posts/dynamodb-single-table/#the-solution-pre-join-your-data-into-item-collections).

### Access Pattern

Let's say we are building a music library with the following features:

* Browsing lots of albums, each of which contains multiple tracks.
* Browsing individual tracks.

This access pattern could be expressed in the following service interface.
```proto
service MusicLibrary {
  rpc GetAlbum(AlbumKey) returns (Album) {}
  rpc GetTrack(TrackKey) returns (Track) {}
}

message Album {
  string album_title = 1;
  string album_artist = 2;
  string release_date = 3;
  string genre = 4;
  repeated Track tracks = 5;
}

message Track {
  string track_title = 1;
  string run_length = 2;
}
```

### Table Schema

The following table schema makes these queries fast and inexpensive.

<table>
  <tbody>
    <tr>
      <td colspan=2 align="center">Primary Key</td>
      <td rowspan=2 colspan=4 align="center" valign="top">Attributes</td>
    </tr>
    <tr>
      <td><strong>partition_key</strong></td>
      <td><strong>sort_key</strong></td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">ALBUM_1</td>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">INFO</td>
      <td><strong>album_title</strong></td>
      <td><strong>album_artiest</strong></td>
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
      <td>ALBUM_1</td>
      <td colspan=5>// More tracks ...</td>
    </tr>
  </tbody>
</table>

Note that

* This table is heterogeneous.
    * it contains two logical types: `AlbumInfo` and `AlbumTrack`.
* This table uses a [composite primary key](https://aws.amazon.com/blogs/database/choosing-the-right-dynamodb-partition-key/), `(parition_key, sort_key)`, to identify each item.
    * The key `("ALBUM_1", "INFO")` identifies `ALBUM_1`'s metadata.
    * The key `("ALBUM_1", "TRACK_1")` identifies `ALBUM_1`'s first track.

### Data Locality

This table stores tracks belonging to the same album together and sorts them by the track number. To render an album, the application only needs to send one request to DynamoDB to get the album info and all the tracks.

```
aws dynamodb query \
    --table-name music_library \
    --key-conditions '{ 
        "PK": { 
            "ComparisonOperator": "EQ",
            "AttributeValueList": [ { "S": "ALBUM_1" } ]
        } 
    }'
```

## Why Tempest?

For locality, we smashed together several logical types in the same table. This improves performance but harms code maintainability.

### DynamoDBMapper API

[`DynamoDBMapper`](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.html), the official Java API, forces you to write weakly-typed code that models the actual persistence type.

```kotlin
@DynamoDBTable(tableName = "music_library")
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

Note that `MusicLibraryItem` is a union type of all the logical types: `AlbumInfo` and `AlbumTrack`. Because all of its attributes are nullable and mutable, code that interacts with it is brittle and error prone.

### Tempest API

Tempest restores maintainability without losing locality. It lets you declare strongly-typed key and item classes for each logical type in the domain layer.

```kotlin
data class AlbumInfoKey(
  val album_token: String
)

data class AlbumInfo(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val album_artist: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""
}

data class AlbumTrackKey(
  val album_token: String,
  val track_token: String
)

data class AlbumTrack(
  @Attribute(name = "partition_key")
  val album_token: String,
  @Attribute(name = "sort_key", prefix = "TRACK_")
  val track_token: String,
  val track_title: String,
  val run_length: String
)
```

You get to build business logic with logical types. Tempest handles mapping them to the underlying persistence type.

```kotlin
interface MusicLibraryTable : LogicalTable<MusicLibraryItem> {
  val albumInfo: InlineView<AlbumInfoKey, AlbumInfo>
  val albumTracks: InlineView<AlbumTrackKey, AlbumTrack>
}

val musicLibrary: MusicLibraryTable

// Load.
fun getAlbumTitle(albumToken: String): String {
  val key = AlbumInfoKey(albumToken)
  val info = musicLibrary.albumInfo.load(key)
  return info.album_title
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
  val albumTracks = musicLibrary.albumTracks.query(
    keyCondition = BeginsWith(AlbumTrackKey(albumToken))
  )
  return albumTracks.map { it.track_title }
}
```

## Get Tempest

With Gradle:

```groovy
implementation "app.cash.tempest:tempest:0.1.0"
```

## Limitation
Tempest depends on the Kotlin reflection API. At the moment, it only supports logical types declared in Kotlin.

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