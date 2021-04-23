package app.cash.tempest2.guides.java;

import app.cash.tempest2.ItemSet;
import app.cash.tempest2.LogicalDb;
import app.cash.tempest2.musiclibrary.java.AlbumTrack;
import app.cash.tempest2.musiclibrary.java.MusicDb;
import app.cash.tempest2.musiclibrary.java.PlaylistInfo;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

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
    DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.create();
    MusicDb db = LogicalDb.create(MusicDb.class, enhancedClient);
  }

  public void logicalDbOptionalConfiguration() {
    DynamoDbClient client = DynamoDbClient.create();
    DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(client)
        .extensions(List.of(/* ... */))
        .build();
    MusicDb db = LogicalDb.create(MusicDb.class, enhancedClient);
  }
}
