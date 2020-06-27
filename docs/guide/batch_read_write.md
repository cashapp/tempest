## Batch Load

`LogicalDb` lets you `batchLoad` multiple items from one or more tables using their primary keys.

```kotlin
val musicDb: MusicDb

fun loadPlaylistTracks(playlist: PlaylistInfo): Set<AlbumTrack> {
  val results = musicDb.batchLoad(
     playlist.track_tokens, // [ AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), AlbumTrack.Key("ALBUM_23", 9) ]
     consistentReads = ConsistentReads.EVENTUAL,
     retryStrategy = DefaultBatchLoadRetryStrategy()
  )
  return results.getItems<AlbumTrack>()
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

```kotlin
val musicDb: MusicDb

fun backfill(
  albumTracksToSave: List<AlbumTrack>, 
  albumTracksToDelete: List<AlbumTrack.Key>
): Boolean {
  val writeSet = BatchWriteSet.Builder()
    .clobber(albumTracksToSave)
    .delete(albumTracksToDelete)
    .build()
  val result = musicDb.batchWrite(
    writeSet,
    retryStrategy = DefaultBatchWriteRetryStrategy()
  )
  return result.isSuccessful
}
```
 