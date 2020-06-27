When two writers write to the same item at the same time, there is a conflict. By default, the last writer wins.

To avoid conflicts in your application, check out these tools:
 
* **Numeric attributes only**: [Atomic counters](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.html#WorkingWithItems.AtomicCounters) models numeric attributes that are incremented, unconditionally, without interfering with other write requests. 
* **Most use cases**: [Optimistic locking](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.OptimisticLocking.html) is a strategy to ensure that the client-side item that you are updating (or deleting) is the same as the item in Amazon DynamoDB. If you use this strategy, your database writes are protected from being overwritten by the writes of others, and vice versa. 

!!! warning "Global tables do not support optimistic locking"
    DynamoDB global tables use a “last writer wins” reconciliation between concurrent updates. If you use global tables, last writer policy wins. So in this case, the locking strategy does not work as expected.

Let's add a playlist feature to our music library:

```kotlin
interface MusicTable : LogicalTable<MusicItem> {
  val playlistInfo: InlineView<PlaylistInfo.Key, PlaylistInfo>
}

data class PlaylistInfo(
  @Attribute(name = "partition_key")
  val playlist_token: String,
  val playlist_name: String,
  val playlist_version: Long,
  val track_tokens: List<AlbumTrack.Key>
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

To serialize writes to the same playlist, we can have all writers implement optimistic locking on the `playlist_version` attribute. 

```kotlin
private val musicTable: MusicTable

fun changePlaylistName(playlistToken: String, newName: String) {
  // Read.
  val existing = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistToken))
  // Modify.
  val newPlaylist = existing.copy(
    playlist_name = newName,
    playlist_version = existing.playlist_version + 1
  )
  // Write.
  musicTable.playlistInfo.save(
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