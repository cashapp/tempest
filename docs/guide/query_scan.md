## Query

In each DynamoDB table and its secondary indexes, items are grouped by partition key and sorted by the sort key.

To query an index, you must provide the name of the partition key attribute and a single value for that attribute. Query returns all items with that partition key value. Optionally, you can provide a sort key attribute and use a comparison operator to refine the search results.

!!! tip "Global secondary index queries cannot fetch attributes from the base table"
    
    A projection is the set of attributes that is copied from a table into a secondary index. The partition key and sort key of the table are always projected into the index; you can project other attributes to support your application's query requirements. When you query an index, Amazon DynamoDB can access any attribute in the projection as if those attributes were in a table of their own.

Let's continue with the music library example.

```kotlin
interface MusicTable : LogicalTable<MusicItem> {
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
  val albumTracksByTitle: SecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack>
}

data class AlbumTrack(
  @Attribute(name = "partition_key")
  val album_token: String,
  @Attribute(name = "sort_key", prefix = "TRACK_")
  val track_token: String,
  val track_title: String,
  val run_length: Duration
) {
  data class Key(
    val album_token: String,
    val track_token: String = ""
  ) {
    constructor(album_token: String, track_number: Long) : this(album_token, "%016x".format(track_number))
  }
  
  @ForIndex("album_track_title_index")
  data class TitleIndexOffset(
    val album_token: String,
    val track_title: String,
    // To uniquely identify an item in pagination.
    val track_token: String? = null
  )
}
```

### Key Condition

#### Partition Key and Entity Type

This uses the primary index to find all tracks in the given album, sorted by track number.

```kotlin
val musicTable: MusicTable

fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
  val page = musicTable.albumTracks.query(
    keyCondition = BeginsWith(
      prefix = AlbumTrack.Key(albumToken)
    )
  )
  return page.contents
}
```

#### Partition Key and Sort Key Prefix

This uses the secondary index to find all tracks in the given album whose title starts with "I want ", sorted by title.
 
```kotlin
val musicTable: MusicTable

fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
  val page = musicTable.albumTracksByTitle.query(
    keyCondition = BeginsWith(
      prefix = AlbumTrack.TitleIndexOffset(albumToken, track_title = "I want ")
    )
  )
  return page.contents
}
```

#### Partition Key and Sort Key Range

This uses the primary index to find track 5 through 9 in the given album, sorted by track number.

```kotlin
val musicTable: MusicTable

fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
  val page = musicTable.albumTracks.query(
    keyCondition = Between(
      startInclusive = AlbumTrack.Key(albumToken, track_number = 5), 
      endInclusive = AlbumTrack.Key(albumToken, track_number = 9))
  )
  return page.contents
}
```

### Descending Order

By default, the sort order is ascending. To reverse the order, set the `asc` parameter to `false`.

```kotlin
val musicTable: MusicTable

fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
  val page = musicTable.albumTracks.query(
    keyCondition = BeginsWith(
      prefix = AlbumTrack.Key(albumToken)
    ),
    asc = false
  )
  return page.contents
}
```

### Filter Expression

If you need to further refine the Query results, you can optionally provide a filter expression. A filter expression determines which items within the Query results should be returned to you. All of the other results are discarded.

!!! tip "Filter expressions do not save cost"
    A filter expression is applied after a Query finishes, but before the results are returned. Therefore, a Query consumes the same amount of read capacity, regardless of whether a filter expression is present.
    
    A Query operation can retrieve a maximum of 1 MB of data. This limit applies before the filter expression is evaluated.
    
    A filter expression cannot contain partition key or sort key attributes. You need to specify those attributes in the key condition expression, not the filter expression.

This find all tracks in the given album that last longer than 3 minutes, sorted by track number.

```kotlin
val musicTable: MusicTable

fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
  val page = musicTable.albumTracks.query(
    keyCondition = BeginsWith(prefix = AlbumTrack.Key(albumToken)),
    filterExpression = runLengthLongerThan(Duration.ofMinutes(3))
  )
  return page.contents
}

private fun runLengthLongerThan(duration: Duration): FilterExpression {
  return FilterExpression(
    "run_length > :duration",
    mapOf(
      ":duration" to AttributeValue().withS(duration.toString())
    )
  )
}

```

### Pagination

```kotlin
val musicTable: MusicTable

fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
  val tracks = mutableListOf<AlbumTrack>()
  var page: Page<AlbumTrack.Key, AlbumTrack>? = null
  do {
    page = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
      pageSize = 10,
      initialOffset = page?.offset
    )
    tracks.addAll(page.contents)
  } while(page?.hasMorePages == true)
  return tracks.toList()
}
```

## Scan

A Scan operation in Amazon DynamoDB reads every item in a table or a secondary index.

By default, the Scan operation processes data sequentially. Amazon DynamoDB returns data to the
application in 1 MB increments, and an application performs additional Scan operations to
retrieve the next 1 MB of data.

```kotlin
val musicTable: MusicTable

fun loadAllAlbumTracks(): List<AlbumTrack> {
  val page = musicTable.albumTracks.scan()
  return page.contents
}
```

### Parallel Scan

The larger the table or index being scanned, the more time the Scan takes to complete. In
addition, a sequential Scan might not always be able to fully use the provisioned read throughput
capacity: Even though DynamoDB distributes a large table's data across multiple physical
partitions, a Scan operation can only read one partition at a time. For this reason, the
throughput of a Scan is constrained by the maximum throughput of a single partition.

To address these issues, the Scan operation can logically divide a table or secondary index into
multiple segments, with multiple application workers scanning the segments in parallel. Each
worker can be a thread (in programming languages that support multithreading) or an operating
system process. To perform a parallel scan, each worker issues its own Scan request with an
unique `WorkerId`.

```kotlin
val musicTable: MusicTable

suspend fun loadAllAlbumTracks(): List<AlbumTrack> {
  val segment1 = async { loadSegment(1) }
  val segment2 = async { loadSegment(2) }
  segment1.await() + segment2.await()
}

suspend fun loadSegment(segment: Int): List<AlbumTrack> { 
  val page = musicTable.albumTracks.scan(
    workerId = WorkerId(segment, totalSegments = 2)
  )
  return page.contents
}
```
### Filter Expression

See query filter expression above.

### Pagination

See query pagination above.