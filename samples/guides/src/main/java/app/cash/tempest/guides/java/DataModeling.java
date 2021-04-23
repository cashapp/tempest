package app.cash.tempest.guides.java;

import app.cash.tempest.ItemSet;
import app.cash.tempest.LogicalDb;
import app.cash.tempest.musiclibrary.java.AlbumTrack;
import app.cash.tempest.musiclibrary.java.MusicDb;
import app.cash.tempest.musiclibrary.java.PlaylistInfo;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import java.util.List;

public class DataModeling {
  private final MusicDb db;

  public DataModeling(MusicDb db) {
    this.db = db;
  }

  public void logicalDbUsage1() {
    ItemSet items = db.batchLoad(
        List.of(
            new AlbumTrack.Key("ALBUM_1", "TRACK_5"),
            new AlbumTrack.Key("ALBUM_2", "TRACK_3"),
            new PlaylistInfo.Key("PLAYLIST_1")));
  }

  public void logicalDbUsage2() {
    AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
    DynamoDBMapper mapper = new DynamoDBMapper(client);
    MusicDb db = LogicalDb.create(MusicDb.class, mapper);
  }

  public void logicalDbOptionalConfiguration() {
    AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
    DynamoDBMapperConfig mapperConfig = DynamoDBMapperConfig.builder()
        .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
        .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
        .withTableNameOverride(null)
        .withPaginationLoadingStrategy(DynamoDBMapperConfig.PaginationLoadingStrategy.EAGER_LOADING)
        .build();
    DynamoDBMapper mapper = new DynamoDBMapper(client, mapperConfig);
    MusicDb db = LogicalDb.create(MusicDb.class, mapper);
  }
}
