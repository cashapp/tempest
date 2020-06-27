We’ve written some examples that demonstrate how to solve common problems with Tempest. Read through them to learn about how everything works together.

```kotlin
interface MusicTable : LogicalTable<MusicItem> {
  val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
}

data class AlbumInfo(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_name: String,
  val release_date: LocalDate,
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
  val track_name: String,
  val track_run_length: Duration
) {
  data class Key(
    val album_token: String,
    val track_token: String
  )
}
```

## Read

Use `load()` to read a value.

```kotlin
private val table: MusicTable

fun getAlbumTitle(albumToken: String): String? {
  val albumInfo = table.albumInfo.load(AlbumInfo.Key(albumToken)) ?: return null
  return albumInfo.album_title
}
```

!!! tip "DynamoDB is eventually consistent by default"

    For actions that only read data, this is usually fine! Once the read completes it could be updated
    anyway, so whether the read reflects very recent writes is typically insignificant. 
    
    If your read immediately follows a write of the same item, you should use a strongly consistent read
    to ensure your read reflects the write.

```kotlin
// Write an item.
val item: AlbumInfo
musicTable.albumInfo.save(item)
// Read that item.
val itemRead = musicTable.albumInfo.load(item.key)
// Note that the value we just read might be older than the value we wrote.
```

If you need to read your writes, you may perform a strongly consistent read at a higher latency.

```kotlin
private val table: MusicTable

fun getAlbumTitle(albumToken: String): String? {
  val albumInfo = table.albumInfo.load(
    AlbumInfo.Key(albumToken), 
    consistentReads = ConsistentReads.CONSISTENT
  ) ?: return null
  return albumInfo.album_title
}
```

## Update

By default, writes are unconditional. When there is a conflict, the last writer wins. 

```kotlin
private val table: MusicTable

fun addAlbum(albumInfo: AlbumInfo) {
  musicTable.albumInfo.save(albumInfo)
}
```

To prevent lost updates across concurrent writes, you may specify a condition expression. If the condition expression evaluates to true, the operation is applied; otherwise, the operation is rolled back.

```kotlin
private val table: MusicTable

fun addAlbum(albumInfo: AlbumInfo) {
  musicTable.albumInfo.save(albumInfo, ifNotExist())
}

fun ifNotExist(): DynamoDBSaveExpression {
  return DynamoDBSaveExpression()
    .withExpectedEntry("partition_key", ExpectedAttributeValue().withExists(false))
}
```

## Delete

Use `delete()` to delete a value by key.

```kotlin
private val table: MusicTable

fun deleteAlbum(albumToken: String) {
  musicTable.albumInfo.delete(AlbumInfo.Key(albumToken))
}
```

Similarly, you can add a condition expression to the delete operation. 

```kotlin
private val table: MusicTable

fun deleteAlbum(albumToken: String) {
  musicTable.albumInfo.delete(AlbumInfo.Key(albumToken), ifExist())
}

fun ifExist(): DynamoDBSaveExpression {
  return DynamoDBSaveExpression()
    .withExpectedEntry("partition_key", ExpectedAttributeValue().withExists(true))
}
```
