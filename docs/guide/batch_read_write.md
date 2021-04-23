## Batch Load

`LogicalDb` lets you `batchLoad` multiple items from one or more tables using their primary keys.

=== "Kotlin - SDK 2.x"

    ```kotlin
    private val db: MusicDb

    fun loadPlaylistTracks(playlist: PlaylistInfo): List<AlbumTrack> {
      val results = db.batchLoad(
        keys = playlist.playlist_tracks, // [AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ...]
        consistentReads = false,
      )
      return results.getItems<AlbumTrack>()
    }
    ```

=== "Java - SDK 2.x"

    ```java
    private final MusicDb db;
    
    public List<AlbumTrack> loadPlaylistTracks(PlaylistInfo playlist) {
      ItemSet results = db.batchLoad(
          // keys.
          playlist.playlist_tracks, // [AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ...]
          // consistentReads.
          false
      );
      return results.getItems(AlbumTrack.class);
    }
    ```

=== "Kotlin - SDK 1.x"

    ```kotlin
    private val db: MusicDb
    
    fun loadPlaylistTracks(playlist: PlaylistInfo): List<AlbumTrack> {
      val results = db.batchLoad(
        keys = playlist.playlist_tracks, // [AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ...]
        consistentReads = ConsistentReads.EVENTUAL,
        retryStrategy = DefaultBatchLoadRetryStrategy()
      )
      return results.getItems<AlbumTrack>()
    }
    ```

=== "Java - SDK 1.x"

    ```java
    private final MusicDb db;
    
    public List<AlbumTrack> loadPlaylistTracks(PlaylistInfo playlist) {
      ItemSet results = db.batchLoad(
          // keys.
          playlist.playlist_tracks, // [AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ...]
          // consistentReads.
          ConsistentReads.EVENTUAL,
          // retryStrategy.
          new DefaultBatchLoadRetryStrategy()
      );
      return results.getItems(AlbumTrack.class);
    }
    ```

!!! tip "Batch load does not return items in any particular order" 

    In order to minimize response latency, BatchGetItem retrieves items in parallel.

    When designing your application, keep in mind that DynamoDB does not return items in any particular order. To help parse the response by item, include the primary key values for the items in your request in the ProjectionExpression parameter.

    If a requested item does not exist, it is not returned in the result. Requests for nonexistent items consume the minimum read capacity units according to the type of read.


## Batch Write

`LogicalDb` lets you batch write and delete multiple items in multiple tables.

!!! tip "Batch writes do not take condition expression" 
    With BatchWriteItem, you can efficiently write or delete large amounts of data, such as from Amazon EMR, or copy data from another database into DynamoDB. In order to improve performance with these large-scale operations, BatchWriteItem does not behave in the same way as individual PutItem and DeleteItem calls would. For example, you cannot specify conditions on individual put and delete requests, and BatchWriteItem does not return deleted items in the response.    

`batchWrite` does not provide transaction guarantees. 
    **Callers should always check the returned `BatchWriteResult`** 
    because this method returns normally even if some writes were not performed.

!!! warning "Batch writes could be partially successful"
    The individual `PutItem` and `DeleteItem` operations specified in BatchWriteItem are atomic; 
    however BatchWriteItem as a whole is not. If any requested operations fail because the table's 
    provisioned throughput is exceeded or an internal processing failure occurs, the failed operations 
    are returned in the UnprocessedItems response parameter.

=== "Kotlin - SDK 2.x"

    ```kotlin
    private val db: MusicDb
    
    fun backfill(
      albumTracksToSave: List<AlbumTrack>,
      albumTracksToDelete: List<AlbumTrack.Key>
    ): Boolean {
      val writeSet = BatchWriteSet.Builder()
        .clobber(albumTracksToSave)
        .delete(albumTracksToDelete)
        .build()
      val result = db.batchWrite(writeSet)
      return result.isSuccessful
    }
    ```

=== "Java - SDK 2.x"

    ```java
    private final MusicDb db;
    
    public boolean backfill(
        List<AlbumTrack> albumTracksToSave,
        List<AlbumTrack.Key> albumTracksToDelete
    ) {
      BatchWriteSet writeSet = new BatchWriteSet.Builder()
          .clobber(albumTracksToSave)
          .delete(albumTracksToDelete)
          .build();
      BatchWriteResult result = db.batchWrite(writeSet);
      return result.isSuccessful();
    }
    ```

=== "Kotlin - SDK 1.x"
    
    ```kotlin
    private val db: MusicDb
    
    fun backfill(
      albumTracksToSave: List<AlbumTrack>,
      albumTracksToDelete: List<AlbumTrack.Key>
    ): Boolean {
      val writeSet = BatchWriteSet.Builder()
        .clobber(albumTracksToSave)
        .delete(albumTracksToDelete)
        .build()
      val result = db.batchWrite(
        writeSet,
        retryStrategy = DefaultBatchWriteRetryStrategy()
      )
      return result.isSuccessful
    }
    ```
 
=== "Java - SDK 1.x"

    ```java
    private final MusicDb db;
    
    public boolean backfill(
        List<AlbumTrack> albumTracksToSave,
        List<AlbumTrack.Key> albumTracksToDelete
    ) {
      BatchWriteSet writeSet = new BatchWriteSet.Builder()
          .clobber(albumTracksToSave)
          .delete(albumTracksToDelete)
          .build();
      BatchWriteResult result = db.batchWrite(
          writeSet,
          // retryStrategy.
          new DefaultBatchWriteRetryStrategy()
      );
      return result.isSuccessful();
    }
    ```

---

Check out the code samples on Github:

 * Music Library ([.kt](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary/src/main/kotlin/app/cash/tempest/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary/src/main/java/app/cash/tempest/musiclibrary/java))
 * Batch Read & Write ([.kt](https://github.com/cashapp/tempest/blob/master/samples/guides/src/main/kotlin/app/cash/tempest/guides/BatchReadWrite.kt), [.java](https://github.com/cashapp/tempest/blob/master/samples/guides/src/main/java/app/cash/tempest/guides/java/BatchReadWrite.java))
 