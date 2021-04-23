package app.cash.tempest2.guides

import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.MusicItem
import app.cash.tempest2.testing.JvmDynamoDbServer
import app.cash.tempest2.testing.TestDynamoDb
import app.cash.tempest2.testing.TestTable
import app.cash.tempest2.testing.logicalDb
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import java.time.LocalDate

class Junit4JVMTest {
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
