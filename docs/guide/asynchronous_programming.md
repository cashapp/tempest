## Nonblocking I/O
The AWS SDK 2.x features [truly nonblocking asynchronous clients](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/asynchronous.html) that implement high 
concurrency across a few threads.

!!! warning "SDK 1.x uses blocking I/O"
    The AWS SDK for Java 1.11.x has asynchronous clients that are wrappers around a thread pool and blocking synchronous clients that don’t provide the full benefit of nonblocking I/O.

## Tempest Async APIs
Tempest for SDK 2.x comes with async APIs that utilize Kotlin [coroutine](https://kotlinlang.org/docs/coroutines-overview.html) and Java [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html).

Declare you DB and tables as `AsyncLogicalDb` and `AsyncLogicalTable`.

=== ":material-language-kotlin: Kotlin - SDK 2.x: 

    ```kotlin
    interface AsyncMusicDb : AsyncLogicalDb {
       @TableName("music_items")
       val music: AsyncMusicTable
    }
    
    interface AsyncMusicTable : AsyncLogicalTable<MusicItem> {
      val albumInfo: AsyncInlineView<AlbumInfo.Key, AlbumInfo>
      val albumTracks: AsyncInlineView<AlbumTrack.Key, AlbumTrack>
    
      val playlistInfo: AsyncInlineView<PlaylistInfo.Key, PlaylistInfo>
    
      // Global Secondary Indexes.
      val albumInfoByGenre: AsyncSecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo>
      val albumInfoByArtist: AsyncSecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo>
    
      // Local Secondary Indexes.
      val albumTracksByTitle: AsyncSecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack>
    }
    ```

=== ":material-language-java: Java - SDK 2.x: 

    ```java
    public interface AsyncMusicDb extends AsyncLogicalDb {
      @TableName("music_items")
      AsyncMusicTable music();
    }
    
    public interface AsyncMusicTable extends AsyncLogicalTable<MusicItem> {
      AsyncInlineView<AlbumInfo.Key, AlbumInfo> albumInfo();
      AsyncInlineView<AlbumTrack.Key, AlbumTrack> albumTracks();
    
      AsyncInlineView<PlaylistInfo.Key, PlaylistInfo> playlistInfo();
    
      // Global Secondary Indexes.
      AsyncSecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo> albumInfoByGenre();
      AsyncSecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo> albumInfoByArtist();
    
      // Local Secondary Indexes.
      AsyncSecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack> albumTracksByTitle();
    }
    ```

Write familiar code that is asynchronous under the hood. 

=== ":material-language-kotlin: Kotlin - SDK 2.x: 

    ```kotlin
    private val table: AsyncMusicTable
  
    suspend fun changePlaylistName(playlistToken: String, newName: String) {
      // Read.
      val existing = checkNotNull(
        table.playlistInfo.load(PlaylistInfo.Key(playlistToken)) // This is a suspend function.
      ) { "Playlist does not exist: $playlistToken" }
      // Modify.
      val newPlaylist = existing.copy(
        playlist_name = newName,
        playlist_version = existing.playlist_version + 1
      )
      // Write.
      table.playlistInfo.save( // This is a suspend function.
        newPlaylist,
        ifPlaylistVersionIs(existing.playlist_version)
      )
    }
  
    private fun ifPlaylistVersionIs(playlist_version: Long): Expression {
      return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(mapOf(":playlist_version" to AttributeValue.builder().n("$playlist_version").build()))
        .build()
    }
    ```

=== ":material-language-java: Java - SDK 2.x: 

    ```java
    private final AsyncMusicTable table;

    public CompletableFuture<Void> changePlaylistName(String playlistToken, String newName) {
      // Read.
      return table.playlistInfo()
          .loadAsync(new PlaylistInfo.Key(playlistToken))
          .thenCompose(existing -> {
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
            return table.playlistInfo()
                .saveAsync(
                    newPlaylist,
                    ifPlaylistVersionIs(existing.playlist_version)
                );
          });
    }

    private Expression ifPlaylistVersionIs(Long playlist_version) {
      return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(
            Map.of(":playlist_version", AttributeValue.builder().n("" + playlist_version).build()))
        .build();
    }
    ```

---

Check out the code samples on Github:

* Music Library - SDK 2.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/kotlin/app/cash/tempest2/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/java/app/cash/tempest2/musiclibrary/java))
* Asynchronous Programing - SDK 2.x ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2/src/main/kotlin/app/cash/tempest2/guides/AsynchronousProgramming.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2/src/main/java/app/cash/tempest2/guides/java/AsynchronousProgramming.java))
