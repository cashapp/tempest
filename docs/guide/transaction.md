Amazon DynamoDB [transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transactions.html) simplify the developer experience of making coordinated, all-or-nothing changes to multiple items both within and across tables. Transactions provide atomicity, consistency, isolation, and durability (ACID) in DynamoDB, helping you to maintain data correctness in your applications.

!!! warning "Other regions could observe partial transactions"
    Transactions are not supported across regions in global tables. For example, if you have a global table with replicas in the US East (Ohio) and US West (Oregon) regions and perform a `TransactWriteItems` operation in the US East (N. Virginia) Region, you may observe partially completed transactions in US West (Oregon) Region as changes are replicated. Changes will only be replicated to other regions once they have been committed in the source region.

## Transactional Read

`LogicalDb` lets you load a consistent snapshot of up to 25 items in a transaction.

=== "Kotlin: 
    
    ```kotlin
    private val db: MusicDb
    
    fun loadPlaylistTracks(playlist: PlaylistInfo) {
      val results = db.transactionLoad(
        playlist.track_tokens // [ AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ... ]
      )
      return results.getItems<AlbumTrack>()
    }
    ```

=== "Java: 

    ```java
    private final MusicDb db;
    
    public List<AlbumTrack> loadPlaylistTracks(PlaylistInfo playlist) {
      ItemSet results = db.transactionLoad(
          playlist.playlist_tracks // [ AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ... ]
      );
      return results.getItems(AlbumTrack.class);
    }
    ```

## Transactional Write

`LogicalDb` lets you update, delete, and condition check up to 25 items atomically. 

The following example uses transactions to make sure it only adds valid album tracks to the playlist. 

=== "Kotlin - SDK 2.x: 

    ```kotlin
    private val db: MusicDb
    private val table: MusicTable
    
    fun addTrackToPlaylist(
      playlistToken: String,
      albumTrack: AlbumTrack.Key
    ) {
      // Read.
      val existing = checkNotNull(
        table.playlistInfo.load(PlaylistInfo.Key(playlistToken))
      ) { "Playlist does not exist: $playlistToken" }
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
      db.transactionWrite(writeSet)
    }

    private fun ifPlaylistVersionIs(playlist_version: Long): Expression {
      return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(
          mapOf(":playlist_version" to AttributeValue.builder().n("$playlist_version").build())
        )
        .build()
    }

    private fun trackExists(): Expression {
      return Expression.builder()
        .expression("attribute_exists(track_title)")
        .build()
    }
    ```

=== "Java - SDK 2.x: 

    ```java
    private final MusicDb db;
    private final MusicTable table;

    public void addTrackToPlaylist(
        String playlistToken,
        AlbumTrack.Key albumTrack
    ) {
      // Read.
      PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
      if (existing == null) {
        throw new IllegalStateException("Playlist does not exist: " + playlistToken);
      }
      // Modify.
      List<AlbumTrack.Key> playlistTrackTokens = new ArrayList<>(existing.playlist_tracks);
      playlistTrackTokens.add(albumTrack);
      PlaylistInfo newPlaylist = new PlaylistInfo(
          existing.playlist_token,
          existing.playlist_name,
          // playlist_tracks.
          playlistTrackTokens,
          // playlist_version.
          existing.playlist_version + 1
      );
      // Write.
      TransactionWriteSet writeSet = new TransactionWriteSet.Builder()
          .save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
          // Add a playlist entry only if the album track exists.
          .checkCondition(albumTrack, trackExists())
          .build();
      db.transactionWrite(writeSet);
    }

    private Expression ifPlaylistVersionIs(Long playlist_version) {
      return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(
          Map.of(":playlist_version", AttributeValue.builder().n("" + playlist_version).build()))
        .build();
    }

    private Expression trackExists() {
      return Expression.builder()
        .expression("attribute_exists(track_title)")
        .build();
    }
    ```

=== "Kotlin - SDK 1.x: 

    ```kotlin
    private val db: MusicDb
    private val table: MusicTable
    
    fun addTrackToPlaylist(
      playlistToken: String,
      albumTrack: AlbumTrack.Key
    ) {
      // Read.
      val existing = checkNotNull(
        table.playlistInfo.load(PlaylistInfo.Key(playlistToken))
      ) { "Playlist does not exist: $playlistToken" }
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
      db.transactionWrite(writeSet)
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

=== "Java - SDK 1.x: 

    ```java
    private final MusicDb db;
    private final MusicTable table;

    public void addTrackToPlaylist(
        String playlistToken,
        AlbumTrack.Key albumTrack
    ) {
      // Read.
      PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
      if (existing == null) {
        throw new IllegalStateException("Playlist does not exist: " + playlistToken);
      }
      // Modify.
      List<AlbumTrack.Key> playlistTrackTokens = new ArrayList<>(existing.playlist_tracks);
      playlistTrackTokens.add(albumTrack);
      PlaylistInfo newPlaylist = new PlaylistInfo(
          existing.playlist_token,
          existing.playlist_name,
          // playlist_tracks.
          playlistTrackTokens,
          // playlist_version.
          existing.playlist_version + 1
      );
      // Write.
      TransactionWriteSet writeSet = new TransactionWriteSet.Builder()
          .save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
          // Add a playlist entry only if the album track exists.
          .checkCondition(albumTrack, trackExists())
          .build();
      db.transactionWrite(writeSet);
    }
    
    private DynamoDBTransactionWriteExpression ifPlaylistVersionIs(Long playlist_version) {
      return new DynamoDBTransactionWriteExpression()
          .withConditionExpression("playlist_version = :playlist_version")
          .withExpressionAttributeValues(
              Map.of(":playlist_version", new AttributeValue().withN("$playlist_version")));
    }
    
    private DynamoDBTransactionWriteExpression trackExists() {
      return new DynamoDBTransactionWriteExpression()
          .withConditionExpression("attribute_exists(track_title)");
    }
    ```

### Writing Pager

To make the 25 item limit easier to work with, we created `WritingPager`: a control flow abstraction for paging transactional writes.

The following example decomposes the operation into multiple transactions containing less than 25 items.

=== "Kotlin: 
    
    ```kotlin
    private val db: MusicDb
    private val table: MusicTable
    
    fun addTracksToPlaylist(
      playlistToken: String,
      albumTracks: List<AlbumTrack.Key>
    ) {
      db.transactionWritingPager(
        albumTracks,
        maxTransactionItems = 25,
        handler = AlbumTrackWritingPagerHandler(playlistToken, table)
      ).execute()
    }
    
    class AlbumTrackWritingPagerHandler(
      private val playlistToken: String,
      private val table: MusicTable
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
        currentPagePlaylistInfo = table.playlistInfo.load(PlaylistInfo.Key(playlistToken))!!
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

=== "Java: 

    ```java
    private final MusicDb db;
    private final MusicTable table;

    public void addTracksToPlaylist(
        String playlistToken,
        List<AlbumTrack.Key> albumTracks
    ) {
      new WritingPager<>(
          db,
          albumTracks,
          // maxTransactionItems.
          25,
          // handler.
          new AlbumTrackWritingPagerHandler(playlistToken, table)
      ).execute();
    }
    
    class AlbumTrackWritingPagerHandler implements WritingPager.Handler<AlbumTrack.Key> {
      private final String playlistToken;
      private final MusicTable table;
      private PlaylistInfo currentPagePlaylistInfo;
      private List<AlbumTrack.Key> currentPageTracks;

      AlbumTrackWritingPagerHandler(String playlistToken,
          MusicTable table) {
        this.playlistToken = playlistToken;
        this.table = table;
      }

      @Override public void eachPage(Function0<Unit> proceed) {
        proceed.invoke();
      }

      @Override public int beforePage(List<AlbumTrack.Key> remainingUpdates,
          int maxTransactionItems) {
        // Reserve 1 for the playlist info at the end.
        currentPageTracks = remainingUpdates.subList(0, maxTransactionItems - 1);
        currentPagePlaylistInfo = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
        return currentPageTracks.size();
      }

      @Override public void item(TransactionWriteSet.Builder builder, AlbumTrack.Key item) {
        builder.checkCondition(item, trackExists());
      }

      @Override public void finishPage(TransactionWriteSet.Builder builder) {
        PlaylistInfo existing = currentPagePlaylistInfo;
        List<AlbumTrack.Key> playlistTrackTokens = new ArrayList<>();
        playlistTrackTokens.addAll(existing.playlist_tracks);
        playlistTrackTokens.addAll(currentPageTracks);
        PlaylistInfo newPlaylist = new PlaylistInfo(
            existing.playlist_token,
            existing.playlist_name,
            // playlist_tracks.
            playlistTrackTokens,
            // playlist_version.
            existing.playlist_version + 1
        );
        builder.save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version));
      }
    }
    ```

---

Check out the code samples on Github:

 * Music Library - SDK 1.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary/src/main/kotlin/app/cash/tempest/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary/src/main/java/app/cash/tempest/musiclibrary/java))
 * Music Library - SDK 2.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/kotlin/app/cash/tempest2/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/java/app/cash/tempest2/musiclibrary/java))
 * Transaction - SDK 1.x ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides/src/main/kotlin/app/cash/tempest/guides/Transaction.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides/src/main/java/app/cash/tempest/guides/java/Transaction.java))
 * Transaction - SDK 2.x ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2/src/main/kotlin/app/cash/tempest2/guides/Transaction.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2/src/main/java/app/cash/tempest2/guides/java/Transaction.java))
 
