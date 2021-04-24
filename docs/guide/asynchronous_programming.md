## Nonblocking I/O
The AWS SDK for Java 2.0 features [truly nonblocking asynchronous clients](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/asynchronous.html) that implement high 
concurrency across a few threads.

## Tempest Async APIs
Tempest for SDK 2.x comes with a set of async APIs that utilizes Kotlin [coroutine](https://kotlinlang.org/docs/coroutines-overview.html) and Java [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html).

=== "Kotlin - SDK 2.x"

    ```kotlin
    private val table: MusicTable
  
    fun changePlaylistName(playlistToken: String, newName: String) = runBlocking {
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

=== "Java - SDK 2.x"

    ```java
    private final MusicTable table;
    
    public void changePlaylistName(String playlistToken, String newName) {
      // Read.
      PlaylistInfo existing = table.playlistInfo()
          .loadAsync(new PlaylistInfo.Key(playlistToken))
          .join(); // This is a completable future.
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
      table.playlistInfo()
          .saveAsync(
              newPlaylist,
              ifPlaylistVersionIs(existing.playlist_version)
          )
          .join(); // This is a completable future.
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

* Music Library - SDK 2.x - Async ([.kt](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary2/src/main/kotlin/app/cash/tempest2/musiclibrary/async), [.java](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary2/src/main/java/app/cash/tempest2/musiclibrary/java/async))
* Asynchronous Programing - SDK 2.x ([.kt](https://github.com/cashapp/tempest/blob/master/samples/guides2/src/main/kotlin/app/cash/tempest2/guides/AsynchronousProgramming.kt), [.java](https://github.com/cashapp/tempest/blob/master/samples/guides2/src/main/java/app/cash/tempest2/guides/java/AsynchronousProgramming.java))
 