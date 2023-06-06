When two writers write to the same item at the same time, there is a conflict. By default, the last writer wins.

To avoid conflicts in your application, check out these tools:
 
* **Numeric attributes only**: [Atomic counters](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.html#WorkingWithItems.AtomicCounters) models numeric attributes that are incremented, unconditionally, without interfering with other write requests. 
* **Most use cases**: [Optimistic locking](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.OptimisticLocking.html) is a strategy to ensure that the client-side item that you are updating (or deleting) is the same as the item in Amazon DynamoDB. If you use this strategy, your database writes are protected from being overwritten by the writes of others, and vice versa. 

!!! warning "Global tables do not support optimistic locking"
    DynamoDB global tables use a “last writer wins” reconciliation between concurrent updates. If you use global tables, last writer policy wins. So in this case, the locking strategy does not work as expected.

Let's add a playlist feature to our music library:

=== "Kotlin: 

    ```kotlin
    interface MusicTable : LogicalTable<MusicItem> {
      val playlistInfo: InlineView<PlaylistInfo.Key, PlaylistInfo>
    }
    
    data class PlaylistInfo(
      @Attribute(name = "partition_key")
      val playlist_token: String,
      val playlist_name: String,
      val playlist_tracks: List<AlbumTrack.Key>,
      val playlist_version: Long = 1
    ) {
      @Attribute(prefix = "INFO_")
      val sort_key: String = ""
    
      data class Key(
        val playlist_token: String
      ) {
        val sort_key: String = ""
      }
    }
    ```

=== "Java: 

    ```java
    public interface MusicTable extends LogicalTable<MusicItem> {
      InlineView<PlaylistInfo.Key, PlaylistInfo> playlistInfo();
    }
    
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
    ```
    
To serialize writes to the same playlist, we can have writers implement optimistic locking on the `playlist_version` attribute. 

=== "Kotlin - SDK 2.x: 

    ```kotlin
    private val table: MusicTable
    
    fun changePlaylistName(playlistToken: String, newName: String) {
      // Read.
      val existing = checkNotNull(
        table.playlistInfo.load(PlaylistInfo.Key(playlistToken))
      ) { "Playlist does not exist: $playlistToken" }
      // Modify.
      val newPlaylist = existing.copy(
        playlist_name = newName,
        playlist_version = existing.playlist_version + 1
      )
      // Write.
      table.playlistInfo.save(
        newPlaylist,
        ifPlaylistVersionIs(existing.playlist_version)
      )
    }
  
    private fun ifPlaylistVersionIs(playlist_version: Long): Expression {
      return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(Map.of(":playlist_version", AttributeValue.builder().n("$playlist_version").build()))
        .build()
    }
    
    ```

=== "Java - SDK 2.x: 

    ```java
    private final MusicTable table;
    
    public void changePlaylistName(String playlistToken, String newName) {
      // Read.
      PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
      if (existing == null) {
        throw new IllegalStateException("Playlist does not exist: " + playlistToken);
      }
      // Modify.
      PlaylistInfo newPlaylist = new PlaylistInfo(
          existing.playlist_token,
          newName,
          existing.playlist_tracks,
          // playlist_version.
          existing.playlist_version + 1
      );
      // Write.
      table.playlistInfo().save(
          newPlaylist,
          ifPlaylistVersionIs(existing.playlist_version)
      );
    }
    
    private Expression ifPlaylistVersionIs(Long playlist_version) {
      return Expression.builder()
          .expression("playlist_version = :playlist_version")
          .expressionValues(Map.of(":playlist_version", AttributeValue.builder().n("" + playlist_version).build()))
          .build();
    }
    ```

=== "Kotlin - SDK 1.x: 

    ```kotlin
    private val table: MusicTable
    
    fun changePlaylistName(playlistToken: String, newName: String) {
      // Read.
      val existing = checkNotNull(
        table.playlistInfo.load(PlaylistInfo.Key(playlistToken))
      ) { "Playlist does not exist: $playlistToken" }
      // Modify.
      val newPlaylist = existing.copy(
        playlist_name = newName,
        playlist_version = existing.playlist_version + 1
      )
      // Write.
      table.playlistInfo.save(
        newPlaylist,
        ifPlaylistVersionIs(existing.playlist_version)
      )
    }
  
    private fun ifPlaylistVersionIs(playlist_version: Long): DynamoDBSaveExpression {
      return DynamoDBSaveExpression()
        .withExpectedEntry(
          "playlist_version",
          ExpectedAttributeValue()
            .withComparisonOperator(ComparisonOperator.EQ)
            .withAttributeValueList(AttributeValue().withN("$playlist_version"))
        )
    }
    ```

=== "Java - SDK 1.x: 
    
    ```java
    private MusicTable table;

    public void changePlaylistName(String playlistToken, String newName) {
      // Read.
      PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
      if (existing == null) {
        throw new IllegalStateException("Playlist does not exist: " + playlistToken);
      }
      // Modify.
      PlaylistInfo newPlaylist = new PlaylistInfo(
          existing.playlist_token,
          newName,
          existing.playlist_tracks,
          // playlist_version.
          existing.playlist_version + 1
      );
      // Write.
      table.playlistInfo().save(
          newPlaylist,
          ifPlaylistVersionIs(existing.playlist_version)
      );
    }
    
    private DynamoDBSaveExpression ifPlaylistVersionIs(Long playlist_version) {
      return new DynamoDBSaveExpression()
          .withExpectedEntry(
              "playlist_version",
              new ExpectedAttributeValue()
                  .withComparisonOperator(ComparisonOperator.EQ)
                  .withAttributeValueList(new AttributeValue().withN("" + playlist_version))
          );
    }
    ```

---

Check out the code samples on Github:

 * Music Library - SDK 1.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary/src/main/kotlin/app/cash/tempest/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary/src/main/java/app/cash/tempest/musiclibrary/java))
 * Music Library - SDK 2.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/kotlin/app/cash/tempest2/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/java/app/cash/tempest2/musiclibrary/java))
 * Optimistic Locking - SDK 1.x ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides/src/main/kotlin/app/cash/tempest/guides/OptimisticLocking.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides/src/main/java/app/cash/tempest/guides/java/OptimisticLocking.java))
 * Optimistic Locking - SDK 2.x ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2/src/main/kotlin/app/cash/tempest2/guides/OptimisticLocking.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2/src/main/java/app/cash/tempest2/guides/java/OptimisticLocking.java))
