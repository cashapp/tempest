We’ve written some examples that demonstrate how to solve common problems with Tempest. Read through them to learn about how everything works together.

=== "Kotlin"

    ```kotlin
    interface MusicTable : LogicalTable<MusicItem> {
      val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
      val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
    }
    
    data class AlbumInfo(
      @Attribute(name = "partition_key")
      val album_token: String,
      val album_title: String,
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
    
=== "Java"

    ```java
    interface MusicTable : LogicalTable<MusicItem> {
      val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
      val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
    }
    
    public class AlbumInfo {
      @Attribute(name = "partition_key")
      public final String album_token;
      public final String album_title;
      public final String artist_name;
      public final LocalDate release_date;
      public final String genre_name;
    
      @Attribute(prefix = "INFO_")
      public final String sort_key = "";
    
      public AlbumInfo(
          String album_token,
          String album_title,
          String artist_name,
          LocalDate release_date,
          String genre_name) {
        this.album_token = album_token;
        this.album_title = album_title;
        this.artist_name = artist_name;
        this.release_date = release_date;
        this.genre_name = genre_name;
      }
    
      public static class Key {
        public final String album_token;
        public final String sort_key = "";
    
        public Key(String album_token) {
          this.album_token = album_token;
        }
      }
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
      }
    }
    ```

## Read

Use `load()` to read a value.

=== "Kotlin"

    ```kotlin
    private val table: MusicTable
    
    fun getAlbumTitle(albumToken: String): String? {
      val albumInfo = table.albumInfo.load(AlbumInfo.Key(albumToken)) ?: return null
      return albumInfo.album_title
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    @Nullable
    public String getAlbumTitle(String albumToken) {
      AlbumInfo albumInfo = table.albumInfo().load(new AlbumInfo.Key(albumToken));
      if (albumInfo == null) {
        return null;
      }
      return albumInfo.album_title;
    }
    ```

!!! tip "DynamoDB is eventually consistent by default"

    For actions that only read data, this is usually fine! Once the read completes it could be updated
    anyway, so whether the read reflects very recent writes is typically insignificant. 
    
    If your read immediately follows a write of the same item, you should use a strongly consistent read
    to ensure your read reflects the write.

=== "Kotlin"

    ```kotlin
    fun readAfterWrite() {
      // Write an item.
      val item = AlbumInfo(
        album_token = "ALBUM_cafcf892",
        album_title = "The Dark Side of the Moon",
        artist_name = "Pink Floyd",
        release_date = LocalDate.of(1973, 3, 1),
        genre_name = "Progressive rock"
      )
      table.albumInfo.save(item)
      // Read that item.
      val itemRead = table.albumInfo.load(item.key)
      // Note that the value we just read might be older than the value we wrote.
    }
    ```

=== "Java"

    ```java
    public void readAfterWrite() {
      // Write an item.
      AlbumInfo item = new AlbumInfo(
          // album_token.
          "ALBUM_cafcf892",
          // album_title.
          "The Dark Side of the Moon",
          // artist_name.
          "Pink Floyd",
          // release_date.
          LocalDate.of(1973, 3, 1),
          // genre_name.
          "Progressive rock"
      );
      table.albumInfo().save(item);
      // Read that item.
      AlbumInfo itemRead = table.albumInfo().load(item.key);
      // Note that the value we just read might be older than the value we wrote.
    }
    ```

If you need to read your writes, you may perform a strongly consistent read at a higher latency.

=== "Kotlin - SDK 2.x"

    ```kotlin
    private val table: MusicTable
    
    fun getAlbumTitle(albumToken: String): String? {
      val albumInfo = table.albumInfo.load(
        AlbumInfo.Key(albumToken), 
        consistentReads = true
      ) ?: return null
      return albumInfo.album_title
    }
    ```

=== "Java - SDK 2.x"

    ```java
    private final MusicTable table;
    
    @Nullable
    public String getAlbumTitle(String albumToken) {
      AlbumInfo albumInfo = table.albumInfo().load(
          new AlbumInfo.Key(albumToken),
          // consistentReads.
          true);
      if (albumInfo == null) {
        return null;
      }
      return albumInfo.album_title;
    }
    ```

=== "Kotlin - SDK 1.x"

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

=== "Java - SDK 1.x"

    ```java
    private final MusicTable table;
    
    @Nullable
    public String getAlbumTitle(String albumToken) {
      AlbumInfo albumInfo = table.albumInfo().load(
          new AlbumInfo.Key(albumToken),
          // consistentReads.
          ConsistentReads.CONSISTENT);
      if (albumInfo == null) {
        return null;
      }
      return albumInfo.album_title;
    }
    ```


## Update

By default, writes are unconditional. When there is a conflict, the last writer wins. 

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun addAlbum(albumInfo: AlbumInfo) {
      table.albumInfo.save(albumInfo)
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public void addAlbum(AlbumInfo albumInfo) {
      table.albumInfo().save(albumInfo);
    }
    ```

To prevent lost updates across concurrent writes, you may specify a condition expression. If the condition expression evaluates to true, the operation is applied; otherwise, the operation is rolled back.

=== "Kotlin - SDK 2.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun addAlbum(albumInfo: AlbumInfo) {
      table.albumInfo.save(albumInfo, ifNotExist())
    }

    private fun ifNotExist(): Expression {
      return Expression.builder()
        .expression("attribute_not_exists(partition_key)")
        .build()
    }
    ```

=== "Java - SDK 2.x"

    ```java
    private final MusicTable table;

    public void addAlbum(AlbumInfo albumInfo) {
      table.albumInfo().save(albumInfo, ifNotExist());
    }

    private Expression ifNotExist() {
      return Expression.builder()
        .expression("attribute_not_exists(partition_key)")
        .build();
    }
    ```

=== "Kotlin - SDK 1.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun addAlbum(albumInfo: AlbumInfo) {
      table.albumInfo.save(albumInfo, ifNotExist())
    }
    
    private fun ifNotExist(): DynamoDBSaveExpression {
      return DynamoDBSaveExpression()
        .withExpectedEntry("partition_key", ExpectedAttributeValue().withExists(false))
    }
    ```

=== "Java - SDK 1.x"

    ```java
    private final MusicTable table;

    public void addAlbum(AlbumInfo albumInfo) {
      table.albumInfo().save(albumInfo, ifNotExist());
    }
  
    private DynamoDBSaveExpression ifNotExist() {
      return new DynamoDBSaveExpression()
          .withExpectedEntry("partition_key", new ExpectedAttributeValue().withExists(false));
    }
    ```

## Delete

Use `delete()` to delete a value by key.

=== "Kotlin"
    
    ```kotlin
    private val table: MusicTable
    
    fun deleteAlbum(albumToken: String) {
      table.albumInfo.delete(AlbumInfo.Key(albumToken))
    }
    ```

=== "Java"

    ```java
    private final MusicTable table;

    public void deleteAlbum(String albumToken) {
      table.albumInfo().deleteKey(new AlbumInfo.Key(albumToken));
    }
    ```

Similarly, you can add a condition expression to the delete operation. 

=== "Kotlin - SDK 2.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun deleteAlbum(albumToken: String) {
      table.albumInfo.delete(AlbumInfo.Key(albumToken), ifExist())
    }

    private fun ifExist(): Expression {
      return Expression.builder()
        .expression("attribute_exists(partition_key)")
        .build()
    }
    ```

=== "Java - SDK 2.x"

    ```java
    private final MusicTable table;
    
    public void deleteAlbum(String albumToken) {
      table.albumInfo().deleteKey(new AlbumInfo.Key(albumToken), ifExist());
    }

    private Expression ifExist() {
      return Expression.builder()
        .expression("attribute_exists(partition_key)")
        .build();
    }
    ```

=== "Kotlin - SDK 1.x"
    
    ```kotlin
    private val table: MusicTable
    
    fun deleteAlbum(albumToken: String) {
      table.albumInfo.delete(AlbumInfo.Key(albumToken), ifExist())
    }
    
    private fun ifExist(): DynamoDBSaveExpression {
      return DynamoDBSaveExpression()
        .withExpectedEntry("partition_key", ExpectedAttributeValue().withExists(true))
    }
    ```

=== "Java - SDK 1.x"

    ```java
    private final MusicTable table;
    
    public void deleteAlbum(String albumToken) {
      table.albumInfo().deleteKey(new AlbumInfo.Key(albumToken), ifExist());
    }
    
    private DynamoDBDeleteExpression ifExist() {
      return new DynamoDBDeleteExpression()
          .withExpectedEntry("partition_key", new ExpectedAttributeValue().withExists(true));
    }
    ```

---

Check out the code samples on Github:

 * Music Library ([.kt](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary/src/main/kotlin/app/cash/tempest/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/master/samples/musiclibrary/src/main/java/app/cash/tempest/musiclibrary/java))
 * CRUD ([.kt](https://github.com/cashapp/tempest/blob/master/samples/guides/src/main/kotlin/app/cash/tempest/guides/Crud.kt), [.java](https://github.com/cashapp/tempest/blob/master/samples/guides/src/main/java/app/cash/tempest/guides/java/Crud.java))
 