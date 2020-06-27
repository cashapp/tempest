Amazon DynamoDB [transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transactions.html) simplify the developer experience of making coordinated, all-or-nothing changes to multiple items both within and across tables. Transactions provide atomicity, consistency, isolation, and durability (ACID) in DynamoDB, helping you to maintain data correctness in your applications.

!!! warning "Other regions could observe partial transactions"
    Transactions are not supported across regions in global tables. For example, if you have a global table with replicas in the US East (Ohio) and US West (Oregon) regions and perform a `TransactWriteItems` operation in the US East (N. Virginia) Region, you may observe partially completed transactions in US West (Oregon) Region as changes are replicated. Changes will only be replicated to other regions once they have been committed in the source region.

## Transactional Read

`LogicalDb` lets you load a consistent snapshot of up to 25 items in a transaction.

```kotlin
val musicDb: MusicDb

fun loadPlaylistTracks(playlist: PlaylistInfo) {
  val results = musicDb.transactionLoad(
    playlist.track_tokens // [ AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), AlbumTrack.Key("ALBUM_23", 9) ]
  )
  return results.getItems<AlbumTrack>()
}
```

## Transactional Update

`LogicalDb` lets you update, delete, and condition check up to 25 items atomically. 

The following example uses transactions to make sure that it only adds valid album tracks to the playlist. 
```kotlin
val musicDb: MusicDb
val musicTable: MusicTable

fun addTrackToPlaylist(
  playlistToken: String, 
  albumTrack: AlbumTrack.Key
) {
  // Read.
  val existing = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistToken))
  // Modify.
  val newPlaylist = existing.copy(
    playlist_tracks = existing.playlist_tracks + albumTrack,
    playlist_version = existing.playlist_version + 1
  )
  // Write.
  val writeSet = TransactionWriteSet.Builder()
    .save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
    // Add a playlist entry only if the album track exists.
    .checkCondition(albumTrack, trackExists())
    .build()
  musicDb.transactionWrite(writeSet)
}

private fun ifPlaylistVersionIs(playlist_version: Long): DynamoDBTransactionWriteExpression {
  return DynamoDBTransactionWriteExpression()
    .withConditionExpression("playlist_version = :playlist_version")
    .withExpressionAttributeValues(
      mapOf(
        ":playlist_version" to AttributeValue().withN("$playlist_version")
      )
    )
}

private fun trackExists(): DynamoDBTransactionWriteExpression {
  return DynamoDBTransactionWriteExpression()
    .withConditionExpression("attribute_exists(track_title)")
}
```

### Writing Pager

To make the 25 item limit easier to work with, we created `WritingPager`, a control flow abstraction for paging transactional writes.

The following example splits the write into multiple transactions if there are more than 25 tracks.

```kotlin
val musicDb: MusicDb
val musicTable: MusicTable

fun addTracksToPlaylist(
  playlistToken: String, 
  albumTracks: List<AlbumTrack.Key>
) {
  musicDb.transactionWritingPager(
    albumTracks,
    maxTransactionItems = 25,
    handler = AlbumTrackWritingPagerHandler(playlistToken, musicTable)
  ).execute()
}

class AlbumTrackWritingPagerHandler(
  private val playlistToken: String,
  private val musicTable: MusicTable
) : WritingPager.Handler<AlbumTrack.Key> {
  private lateinit var currentPagePlaylistInfo: PlaylistInfo
  private lateinit var currentPageTracks: List<AlbumTrack.Key>

  override fun eachPage(proceed: () -> Unit) {
    proceed()
  }

  override fun beforePage(
    remainingUpdates: List<AlbumTrack.Key>,
    maxTransactionItems: Int
  ): Int {
    // Reserve 1 for the playlist info at the end.
    currentPageTracks = remainingUpdates.take((maxTransactionItems - 1))
    currentPagePlaylistInfo = musicTable.playlistInfo.load(PlaylistInfo.Key(playlistToken))!!
    return currentPageTracks.size
  }

  override fun item(builder: TransactionWriteSet.Builder, item: AlbumTrack.Key) {
    builder.checkCondition(item, trackExists())
  }

  override fun finishPage(builder: TransactionWriteSet.Builder) {
    val existing = currentPagePlaylistInfo
    val newPlaylist = existing.copy(
      playlist_tracks = existing.playlist_tracks + currentPageTracks,
      playlist_version = existing.playlist_version + 1
    )
    builder.save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
  }
}
```