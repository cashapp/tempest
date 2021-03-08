## Tempest Testing

Tempest provides a library for testing DynamoDB clients
using [DynamoDBLocal](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html)
. It comes with two implementations:

- **JVM:** This is the preferred option, running a `DynamoDBProxyServer` backed by `sqlite4java`,
  which is available on most platforms.
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

Then in tests annotated `@org.junit.jupiter.api.Test`, you may add `TestDynamoDb` as a test
[extension](https://junit.org/junit5/docs/current/user-guide/#extensions). This extension spins up a
DynamoDB server. It shares the server across tests and keeps it running until the process exits. It
also manages test tables for you, recreating them before each test.

```kotlin
class MyTest {
  @RegisterExtension
  @JvmField
  val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
    // Tempest recreates this `@DynamoDBTable` before each test.
    .addTable(TestTable.create<MusicItem>())
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
    val result = db.dynamoDb.describeTable("table_name")
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

Then in tests annotated `@org.junit.Test`, you may add `TestDynamoDb` as a
test [rule](https://junit.org/junit4/javadoc/4.12/org/junit/Rule.html). This rule spins up a
DynamoDB server. It shares the server across tests and keeps it running until the process exits. It
also manages test tables for you, recreating them before each test.

```kotlin
class MyTest {
  @get:Rule
  val db = TestDynamoDb.Builder(JvmDynamoDbServer.Factory)
    // Tempest recreates this `@DynamoDBTable` before each test.
    .addTable(TestTable.create<MusicItem>())
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
    val result = db.dynamoDb.describeTable("table_name")
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
