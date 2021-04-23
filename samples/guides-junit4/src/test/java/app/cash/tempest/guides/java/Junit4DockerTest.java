package app.cash.tempest.guides.java;

import app.cash.tempest.musiclibrary.java.AlbumInfo;
import app.cash.tempest.musiclibrary.java.MusicDb;
import app.cash.tempest.musiclibrary.java.MusicItem;
import app.cash.tempest.musiclibrary.java.MusicTable;
import app.cash.tempest.testing.DockerDynamoDbServer;
import app.cash.tempest.testing.TestDynamoDb;
import app.cash.tempest.testing.TestTable;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class Junit4DockerTest {
  @Rule
  public TestDynamoDb db = new TestDynamoDb.Builder(DockerDynamoDbServer.Factory.INSTANCE)
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
    DescribeTableResult result = db.getDynamoDb().describeTable("j_music_items");
    // Do something with the result...
  }
}
