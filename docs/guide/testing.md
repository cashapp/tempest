## Tempest Testing

Tempest provides a library for testing DynamoDB clients
using [DynamoDBLocal](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html)
. It comes with two implementations:

- **JVM:** This is the preferred option, running a `DynamoDBProxyServer` backed by `sqlite4java`,
  which is available on [most platforms](https://github.com/cashapp/tempest/blob/24beb08b2be88d3666b5b64879618d01771b529a/tempest-testing-jvm/src/main/kotlin/app/cash/tempest/testing/JvmDynamoDbServer.kt#L66).
- **Docker:** This runs [dynamodb-local](https://hub.docker.com/r/amazon/dynamodb-local) in a Docker
  container.

Feature matrix:

Feature         |tempest-testing-jvm        |tempest-testing-docker
----------------|---------------------------|----------------------
Start up time   |~1s                        |~10s
Memory usage    |Less                       |More
Dependency      |sqlite4java native library |Docker

## JUnit 5 Integration

To use `tempest-testing`, first add this library as a test dependency:

For AWS SDK 1.x:

```groovy
dependencies {
  testImplementation "app.cash.tempest:tempest-testing-jvm:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest-testing-junit5:{{ versions.tempest }}"
}
// Or
dependencies {
  testImplementation "app.cash.tempest:tempest-testing-docker:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest-testing-junit5:{{ versions.tempest }}"
}
```

For AWS SDK 2.x:

```groovy
dependencies {
  testImplementation "app.cash.tempest:tempest2-testing-jvm:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest2-testing-junit5:{{ versions.tempest }}"
}
// Or
dependencies {
  testImplementation "app.cash.tempest:tempest2-testing-docker:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest2-testing-junit5:{{ versions.tempest }}"
}
```

Then in tests annotated with `@org.junit.jupiter.api.Test`, you may add `TestDynamoDb` as a test
[extension](https://junit.org/junit5/docs/current/user-guide/#extensions). This extension spins up a
DynamoDB server. It shares the server across tests and keeps it running until the process exits. It
also manages test tables for you, recreating them before each test.

=== "Kotlin - SDK 2.x: 

    ```kotlin
    class MyTest {
      @RegisterExtension
      @JvmField
      val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem.TABLE_NAME, MusicItem::class.java))
          .build()
    
      private val musicTable by lazy { db.logicalDb<MusicDb>().music }
    
      @Test
      fun test() {
        val albumInfo = AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        )
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo.save(albumInfo)
      }
    
      @Test
      fun anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        val result = db.dynamoDb.describeTable(
                DescribeTableRequest.builder().tableName(MusicItem.TABLE_NAME).build()
        )
        // Do something with the result...
      }
    }
    ```

=== "Java - SDK 2.x: 

    ```java
    class MyTest {
      @RegisterExtension
      TestDynamoDb db = new TestDynamoDb.Builder(JvmDynamoDbServer.Factory.INSTANCE)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem.TABLE_NAME, MusicItem.class))
          .build();
    
      MusicTable musicTable;
    
      @BeforeEach
      public void setup() {
        musicTable = db.logicalDb(MusicDb.class).music();
      }
    
      @Test
      public void test() {
        AlbumInfo albumInfo = new AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        );
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo().save(albumInfo);
      }
    
      @Test
      public void anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        DescribeTableResponse result = db.getDynamoDb().describeTable(
                DescribeTableRequest.builder().tableName(MusicItem.TABLE_NAME).build()
        );
        // Do something with the result...
      }
    }
    ```

=== "Kotlin - SDK 1.x: 

    ```kotlin
    class MyTest {
      @RegisterExtension
      @JvmField
      val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem::class.java))
          .build()
    
      private val musicTable by lazy { db.logicalDb<MusicDb>().music }
    
      @Test
      fun test() {
        val albumInfo = AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        )
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo.save(albumInfo)
      }
    
      @Test
      fun anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        val result = db.dynamoDb.describeTable("music_items")
        // Do something with the result...
      }
    }
    ```

=== "Java - SDK 1.x: 

    ```java
    class MyTest {
      @RegisterExtension
      TestDynamoDb db = new TestDynamoDb.Builder(JvmDynamoDbServer.Factory.INSTANCE)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem.class))
          .build();
    
      MusicTable musicTable;
    
      @BeforeEach
      public void setup() {
        musicTable = db.logicalDb(MusicDb.class).music();
      }
    
      @Test
      public void test() {
        AlbumInfo albumInfo = new AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        );
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo().save(albumInfo);
      }
    
      @Test
      public void anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        DescribeTableResult result = db.getDynamoDb().describeTable("music_items");
        // Do something with the result...
      }
    }
    ```

To customize test tables, mutate the `CreateTableRequest` in a lambda.

```kotlin
fun testDb() = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
  .addTable(
    TestTable.create<MusicItem> { createTableRequest ->
      for (gsi in createTableRequest.globalSecondaryIndexes) {
        gsi.withProjection(Projection().withProjectionType(ProjectionType.ALL))
      }
      createTableRequest
    }
  )
  .build()
```

To use the Docker implementation, specify it in the builder.

```kotlin
fun testDb() = TestDynamoDb.Builder(DockerDynamoDbServer.Factory)
  .addTable(TestTable.create<MusicItem>())
  .build()
```

## JUnit 4 Integration

To use `tempest-testing`, first add this library as a test dependency:

For AWS SDK 1.x:

```groovy
dependencies {
  testImplementation "app.cash.tempest:tempest-testing-jvm:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest-testing-junit4:{{ versions.tempest }}"
}
// Or
dependencies {
  testImplementation "app.cash.tempest:tempest-testing-docker:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest-testing-junit4:{{ versions.tempest }}"
}
```

For AWS SDK 2.x:

```groovy
dependencies {
  testImplementation "app.cash.tempest:tempest2-testing-jvm:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest2-testing-junit4:{{ versions.tempest }}"
}
// Or
dependencies {
  testImplementation "app.cash.tempest:tempest2-testing-docker:{{ versions.tempest }}"
  testImplementation "app.cash.tempest:tempest2-testing-junit4:{{ versions.tempest }}"
}
```

Then in tests annotated with `@org.junit.Test`, you may add `TestDynamoDb` as a
test [rule](https://junit.org/junit4/javadoc/4.12/org/junit/Rule.html). This rule spins up a
DynamoDB server. It shares the server across tests and keeps it running until the process exits. It
also manages test tables for you, recreating them before each test.

=== "Kotlin - SDK 2.x: 

    ```kotlin
    class MyTest {
      @get:Rule
      val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem.TABLE_NAME, MusicItem::class.java))
          .build()
    
      private val musicTable by lazy { db.logicalDb<MusicDb>().music }
    
      @Test
      fun test() {
        val albumInfo = AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        )
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo.save(albumInfo)
      }
    
      @Test
      fun anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        val result = db.dynamoDb.describeTable(
                DescribeTableRequest.builder().tableName(MusicItem.TABLE_NAME).build()
        )
        // Do something with the result...
      }
    }
    ```

=== "Java - SDK 2.x: 

    ```java
    class MyTest {
      @Rule
      public TestDynamoDb db = new TestDynamoDb.Builder(JvmDynamoDbServer.Factory.INSTANCE)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem.TABLE_NAME, MusicItem.class))
          .build();
    
      MusicTable musicTable;
    
      @Before
      public void setup() {
        musicTable = db.logicalDb(MusicDb.class).music();
      }
    
      @Test
      public void test() {
        AlbumInfo albumInfo = new AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        );
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo().save(albumInfo);
      }
    
      @Test
      public void anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        DescribeTableResponse result = db.getDynamoDb().describeTable(
                DescribeTableRequest.builder().tableName(MusicItem.TABLE_NAME).build()
        );
        // Do something with the result...
      }
    }
    ```

=== "Kotlin - SDK 1.x: 

    ```kotlin
    class MyTest {
      @get:Rule
      val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem::class.java))
          .build()
    
      private val musicTable by lazy { db.logicalDb<MusicDb>().music }
    
      @Test
      fun test() {
        val albumInfo = AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        )
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo.save(albumInfo)
      }
    
      @Test
      fun anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        val result = db.dynamoDb.describeTable("music_items")
        // Do something with the result...
      }
    }
    ```

=== "Java - SDK 1.x: 

    ```java
    class MyTest {
      @Rule
      public TestDynamoDb db = new TestDynamoDb.Builder(JvmDynamoDbServer.Factory.INSTANCE)
          // `MusicItem` is annotated with `@DynamoDBTable`. Tempest recreates this table before each test.
          .addTable(TestTable.create(MusicItem.class))
          .build();
    
      MusicTable musicTable;
    
      @Before
      public void setup() {
        musicTable = db.logicalDb(MusicDb.class).music();
      }
    
      @Test
      public void test() {
        AlbumInfo albumInfo = new AlbumInfo(
            "ALBUM_1",
            "after hours - EP",
            "53 Thieves",
            LocalDate.of(2020, 2, 21),
            "Contemporary R&B"
        );
        // Talk to DynamoDB using Tempest's API.
        musicTable.albumInfo().save(albumInfo);
      }
    
      @Test
      public void anotherTest() {
        // Talk to DynamoDB using the AWS SDK.
        DescribeTableResult result = db.getDynamoDb().describeTable("music_items");
        // Do something with the result...
      }
    }
    ```

To customize test tables, mutate the `CreateTableRequest` in a lambda.

```kotlin
fun testDb() = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
  .addTable(
    TestTable.create<MusicItem> { createTableRequest ->
      for (gsi in createTableRequest.globalSecondaryIndexes) {
        gsi.withProjection(Projection().withProjectionType(ProjectionType.ALL))
      }
      createTableRequest
    }
  )
  .build()
```

To use the Docker implementation, specify it in the builder.

```kotlin
fun testDb() = TestDynamoDb.Builder(DockerDynamoDbServer.Factory)
  .addTable(TestTable.create<MusicItem>())
  .build()
```

## Other Testing Frameworks

Tempest testing is compatible with other testing frameworks. You'll need to write your own integration code. Feel free to reference the implementations above. Here is a simpler example:

```kotlin
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
// ...

class JUnit5TestDynamoDb(
  private val testTables: List<TestTable>,
) : BeforeEachCallback, AfterEachCallback {

  private val service = TestDynamoDbService.create(JvmDynamoDbServer.Factory, testTables, 8000)
          
  override fun beforeEach(context: ExtensionContext) {
    service.startAsync()
    service.awaitRunning()
  }

  override fun afterEach(context: ExtensionContext?) {
    service.stopAsync()
    service.awaitTerminated()
  }
}
```

---

Check out the code samples on Github:

* Music Library - SDK 1.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary/src/main/kotlin/app/cash/tempest/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary/src/main/java/app/cash/tempest/musiclibrary/java))
* Music Library - SDK 2.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/kotlin/app/cash/tempest2/musiclibrary), [.java](https://github.com/cashapp/tempest/tree/main/samples/musiclibrary2/src/main/java/app/cash/tempest2/musiclibrary/java))
* Testing - SDK 1.x - JUnit4 - JVM ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides-junit4/src/test/kotlin/app/cash/tempest/guides/Junit4JVMTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides-junit4/src/test/java/app/cash/tempest/guides/java/Junit4JVMTest.java))
* Testing - SDK 1.x - JUnit4 - Docker ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides-junit4/src/test/kotlin/app/cash/tempest/guides/Junit4DockerTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides-junit4/src/test/java/app/cash/tempest/guides/java/Junit4DockerTest.java))
* Testing - SDK 1.x - JUnit5 - JVM ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides-junit5/src/test/kotlin/app/cash/tempest/guides/Junit5JVMTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides-junit5/src/test/java/app/cash/tempest/guides/java/Junit5JVMTest.java))
* Testing - SDK 1.x - JUnit5 - Docker ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides-junit5/src/test/kotlin/app/cash/tempest/guides/Junit5DockerTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides-junit5/src/test/java/app/cash/tempest/guides/java/Junit5DockerTest.java))
* Testing - SDK 2.x - JUnit4 - JVM ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit4/src/test/kotlin/app/cash/tempest2/guides/Junit4JVMTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit4/src/test/java/app/cash/tempest2/guides/java/Junit4JVMTest.java))
* Testing - SDK 2.x - JUnit4 - Docker ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit4/src/test/kotlin/app/cash/tempest2/guides/Junit4DockerTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit4/src/test/java/app/cash/tempest2/guides/java/Junit4DockerTest.java))
* Testing - SDK 2.x - JUnit5 - JVM ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit5/src/test/kotlin/app/cash/tempest2/guides/Junit5JVMTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit5/src/test/java/app/cash/tempest2/guides/java/Junit5JVMTest.java))
* Testing - SDK 2.x - JUnit5 - Docker ([.kt](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit5/src/test/kotlin/app/cash/tempest2/guides/Junit5DockerTest.kt), [.java](https://github.com/cashapp/tempest/blob/main/samples/guides2-junit5/src/test/java/app/cash/tempest2/guides/java/Junit5DockerTest.java))
