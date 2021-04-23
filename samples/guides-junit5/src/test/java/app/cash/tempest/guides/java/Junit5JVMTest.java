package app.cash.tempest.guides.java;

import app.cash.tempest.musiclibrary.java.AlbumInfo;
import app.cash.tempest.musiclibrary.java.MusicTable;
import app.cash.tempest.musiclibrary.java.MusicDb;
import app.cash.tempest.musiclibrary.java.MusicItem;
import app.cash.tempest.testing.JvmDynamoDbServer;
import app.cash.tempest.testing.TestDynamoDb;
import app.cash.tempest.testing.TestTable;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class Junit5JVMTest {
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
    DescribeTableResult result = db.getDynamoDb().describeTable("j_music_items");
    // Do something with the result...
  }
}
