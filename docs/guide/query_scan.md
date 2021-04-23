## Query

In each DynamoDB table and its secondary indexes, items are grouped by partition key and sorted by the sort key.

To query an index, you must provide the name of the partition key attribute and a single value for that attribute. Query returns all items with that partition key value. Optionally, you can provide a sort key attribute and use a comparison operator to refine the search results.

!!! tip "Global secondary index queries cannot fetch attributes from the base table"
    
    A projection is the set of attributes that is copied from a table into a secondary index. The partition key and sort key of the table are always projected into the index; you can project other attributes to support your application's query requirements. When you query an index, Amazon DynamoDB can access any attribute in the projection as if those attributes were in a table of their own.

Let's continue with the music library example.

=== "Kotlin"

    ```kotlin
    interface MusicTable : LogicalTable<MusicItem> {
      val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
      // Local Secondary Indexes.
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
      )
      
      @ForIndex("album_track_title_index")
      data class TitleIndexOffset(
        val album_token: String,
        val track_title: String,
        // To uniquely identify an item in pagination.
        val track_token: String? = null
      )
    }
    ```

=== "Java"

    ```java
    public interface MusicTable extends LogicalTable<MusicItem> {
      InlineView<AlbumTrack.Key, AlbumTrack> albumTracks();
      // Local Secondary Indexes.
      SecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack> albumTracksByTitle();
    }

    public class AlbumTrack {
      @Attribute(name = "partition_key")
      public final String album_token;
      @Attribute(name = "sort_key", prefix = "TRACK_")
      public final String track_token;
      public final String track_title;
      public final Duration run_length;

      public AlbumTrack(
          String album_token,
          String track_token,
          String track_title,
          Duration run_length) {
        this.album_token = album_token;
        this.track_token = track_token;
        this.track_title = track_title;
        this.run_length = run_length;
      }

      public static class Key {
        public final String album_token;
        public final String track_token;
    
        public Key(String album_token, String track_token) {
          this.album_token = album_token;
          this.track_token = track_token;
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
    ```
    
### Key Condition

#### Partition Key and Entity Type

This uses the primary index to find all tracks in the given album, sorted by track number.

=== "Kotlin"

    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val page = table.albumTracks.query(
        keyCondition = BeginsWith(
          prefix = AlbumTrack.Key(albumToken)
        )
      )
      return page.contents
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
          // keyCondition.
          new BeginsWith<>(
              // prefix.
              new AlbumTrack.Key(albumToken)
          )
      );
      return page.getContents();
    }
    ```

#### Partition Key and Sort Key Prefix

This uses the secondary index to find all tracks in the given album whose title starts with "I want ", sorted by title.
 
=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val page = table.albumTracksByTitle.query(
        keyCondition = BeginsWith(
          prefix = AlbumTrack.TitleIndexOffset(albumToken, track_title = "I want ")
        )
      )
      return page.contents
    }
    ```
    
=== "Java"

    ```java
    private final MusicTable table;
    
    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      Page<AlbumTrack.TitleIndexOffset, AlbumTrack> page = table.albumTracksByTitle().query(
          // keyCondition.
          new BeginsWith<>(
              // prefix.
              new AlbumTrack.TitleIndexOffset(albumToken, "I want ")
          )
      );
      return page.getContents();
    }
    ```

#### Partition Key and Sort Key Range

This uses the primary index to find track 5 through 9 in the given album, sorted by track number.

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val page = table.albumTracks.query(
        keyCondition = Between(
          startInclusive = AlbumTrack.Key(albumToken, track_number = 5), 
          endInclusive = AlbumTrack.Key(albumToken, track_number = 9))
      )
      return page.contents
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
          // keyCondition.
          new Between<>(
              // startInclusive.
              new AlbumTrack.Key(albumToken, /* track_number */ 5L),
              // endInclusive.
              new AlbumTrack.Key(albumToken, /* track_number */ 9L))
      );
      return page.getContents();
    }
    ```

### Descending Order

By default, the sort order is ascending. To reverse the order, set the `asc` parameter to `false`.

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val page = table.albumTracks.query(
        keyCondition = BeginsWith(
          prefix = AlbumTrack.Key(albumToken)
        ),
        asc = false
      )
      return page.contents
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;
    
    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
          // keyCondition.
          new BeginsWith<>(
              // prefix.
              new AlbumTrack.Key(albumToken)
          ),
          // config.
          new QueryConfig.Builder()
              .asc(false)
              .build()
      );
      return page.getContents();
    }
    ```

### Filter Expression

If you need to further refine the Query results, you can optionally provide a filter expression. A filter expression determines which items within the Query results should be returned to you. All of the other results are discarded.

!!! tip "Filter expressions do not save cost"
    A filter expression is applied after a Query finishes, but before the results are returned. Therefore, a Query consumes the same amount of read capacity, regardless of whether a filter expression is present.
    
    A Query operation can retrieve a maximum of 1 MB of data. This limit applies before the filter expression is evaluated.
    
    A filter expression cannot contain partition key or sort key attributes. You need to specify those attributes in the key condition expression, not the filter expression.

This find all tracks in the given album that last longer than 3 minutes, sorted by track number.

=== "Kotlin - SDK 2.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val page = table.albumTracks.query(
        keyCondition = BeginsWith(prefix = AlbumTrack.Key(albumToken)),
        filterExpression = runLengthLongerThan(Duration.ofMinutes(3))
      )
      return page.contents
    }

    private fun runLengthLongerThan(duration: Duration): Expression {
      return Expression.builder()
        .expression("run_length > :duration")
        .expressionValues(
           mapOf(
            ":duration" to AttributeValue.builder().s(duration.toString()).build()))
        .build()
    }
    ```

=== "Java - SDK 2.x"

    ```java
    private final MusicTable table;
    
    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
          // keyCondition.
          new BeginsWith<>(
              // prefix.
              new AlbumTrack.Key(albumToken)
          ),
          // config.
          new QueryConfig.Builder()
              .filterExpression(runLengthLongerThan(Duration.ofMinutes(3)))
              .build()
      );
      return page.getContents();
    }

    private Expression runLengthLongerThan(Duration duration) {
      return Expression.builder()
        .expression("run_length > :duration")
        .expressionValues(
          Map.of(":duration", AttributeValue.builder().s(duration.toString()).build()))
        .build();
    }
    ```

=== "Kotlin - SDK 1.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val page = table.albumTracks.query(
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

=== "Java  - SDK 1.x"

    ```java
    private final MusicTable table;
    
    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
          // keyCondition.
          new BeginsWith<>(
              // prefix.
              new AlbumTrack.Key(albumToken)
          ),
          // config.
          new QueryConfig.Builder()
              .filterExpression(runLengthLongerThan(Duration.ofMinutes(3)))
              .build()
      );
      return page.getContents();
    }
  
    private FilterExpression runLengthLongerThan(Duration duration) {
      return new FilterExpression(
          "run_length > :duration",
          Map.of(":duration", new AttributeValue().withS(duration.toString()))
      );
    }
    ```

### Pagination

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
      val tracks = mutableListOf<AlbumTrack>()
      var page: Page<AlbumTrack.Key, AlbumTrack>? = null
      do {
        page = table.albumTracks.query(
          keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
          pageSize = 10,
          initialOffset = page?.offset
        )
        tracks.addAll(page.contents)
      } while(page?.hasMorePages == true)
      return tracks.toList()
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public List<AlbumTrack> loadAlbumTracks(String albumToken) {
      List<AlbumTrack> tracks = new ArrayList<>();
      Page<AlbumTrack.Key, AlbumTrack> page = null;
      do {
        page = table.albumTracks().query(
            // keyCondition.
            new BeginsWith<>(new AlbumTrack.Key(albumToken)),
            // config.
            new QueryConfig.Builder()
                .pageSize(10)
                .build(),
            // initialOffset.
            page != null ? page.getOffset() : null
        );
        tracks.addAll(page.getContents());
      } while (page.getHasMorePages());
      return tracks;
    }
    ```

#### Specifying the Offset

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAlbumTracksAfterTrack(albumToken: String, trackToken: String): List<AlbumTrack> {
      val tracks = mutableListOf<AlbumTrack>()
      var page: Page<AlbumTrack.Key, AlbumTrack>? = null
      val offset = Offset(AlbumTrack.Key(trackToken))
      do {
        page = table.albumTracks.query(
          keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
          pageSize = 10,
          initialOffset = page?.offset ?: offset
        )
        tracks.addAll(page.contents)
      } while (page?.hasMorePages == true)
      return tracks.toList()
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public List<AlbumTrack> loadAlbumTracksAfterTrack(String albumToken, String trackToken) {
      List<AlbumTrack> tracks = new ArrayList<>();
      Page<AlbumTrack.Key, AlbumTrack> page = null;
      Offset<AlbumTrack.Key> firstOffset = new Offset<>(new AlbumTrack.Key(albumToken, trackToken));
  
      do {
        page = table.albumTracks().query(
            // keyCondition.
            new BeginsWith<>(new AlbumTrack.Key(albumToken)),
            // config.
            new QueryConfig.Builder()
                    .pageSize(10)
                    .build(),
            // initialOffset.
            page != null ? page.getOffset() : firstOffset
        );
        tracks.addAll(page.getContents());
      } while (page.getHasMorePages());
      return tracks;
    }
    ```

## Scan

A Scan operation in Amazon DynamoDB reads every item in a table or a secondary index.

By default, the Scan operation processes data sequentially. Amazon DynamoDB returns data to the
application in 1 MB increments, and an application performs additional Scan operations to
retrieve the next 1 MB of data.

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAllAlbumTracks(): List<AlbumTrack> {
      val page = table.albumTracks.scan()
      return page.contents
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public List<AlbumTrack> loadAllAlbumTracks() {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().scan();
      return page.getContents();
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

=== "Kotlin - SDK 2.x"
    
    ```kotlin
    Not supported
    ```

=== "Java - SDK 2.x"

    ```java
    Not supported
    ```

=== "Kotlin - SDK 1.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun loadAllAlbumTracks(): List<AlbumTrack> = runBlocking {
      val segment1 = async { loadSegment(1) }
      val segment2 = async { loadSegment(2) }
      segment1.await() + segment2.await()
    }
  
    private fun loadSegment(segment: Int): List<AlbumTrack> {
      val page = table.albumTracks.scan(
        workerId = WorkerId(segment, totalSegments = 2)
      )
      return page.contents
    }
    ```

=== "Java - SDK 1.x"

    ```java
    private final MusicTable table;
    private final ExecutorService executor;

    public List<AlbumTrack> loadAllAlbumTracks() {
      Future<List<AlbumTrack>> segment1 = executor.submit(() -> loadSegment(1));
      Future<List<AlbumTrack>> segment2 = executor.submit(() -> loadSegment(2));
      List<AlbumTrack> results = new ArrayList<>();
      try {
        results.addAll(segment1.get());
        results.addAll(segment2.get());
      } catch (InterruptedException | ExecutionException e) {
        throw new IllegalStateException("Failed to load tracks", e);
      }
      return results;
    }
  
    private List<AlbumTrack> loadSegment(int segment) {
      Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().scan(
          new ScanConfig.Builder()
              .workerId(new WorkerId(segment, /* totalSegments */ 2))
              .build()
      );
      return page.getContents();
    }
    ```

### Filter Expression

See query filter expression above.

### Pagination

See query pagination above.

---

Check out the code samples on Github:

 * Music Library ([.kt](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary/src/main/kotlin/app/cash/tempest/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary/src/main/java/app/cash/tempest/musiclibrary/java))
 * Query & Scan ([.kt](https://github.com/cashapp/tempest/blob/master/samples/guides/src/main/kotlin/app/cash/tempest/guides/QueryNScan.kt), [.java](https://github.com/cashapp/tempest/blob/master/samples/guides/src/main/java/app/cash/tempest/guides/java/QueryNScan.java))
 