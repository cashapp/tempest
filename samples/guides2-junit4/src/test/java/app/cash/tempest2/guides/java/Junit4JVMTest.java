package app.cash.tempest2.guides.java;

import app.cash.tempest2.musiclibrary.java.AlbumInfo;
import app.cash.tempest2.musiclibrary.java.MusicDb;
import app.cash.tempest2.musiclibrary.java.MusicItem;
import app.cash.tempest2.musiclibrary.java.MusicTable;
import app.cash.tempest2.testing.JvmDynamoDbServer;
import app.cash.tempest2.testing.TestDynamoDb;
import app.cash.tempest2.testing.TestTable;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;

public class Junit4JVMTest {
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
