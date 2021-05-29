package app.cash.tempest2.guides.java;

import app.cash.tempest2.musiclibrary.java.AsyncMusicTable;
import app.cash.tempest2.musiclibrary.java.PlaylistInfo;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class AsynchronousProgramming {

  private final AsyncMusicTable table;

  public AsynchronousProgramming(AsyncMusicTable table) {
    this.table = table;
  }

  public CompletableFuture<Void> changePlaylistName(String playlistToken, String newName) {
    // Read.
    return table.playlistInfo()
        .loadAsync(new PlaylistInfo.Key(playlistToken))
        .thenCompose(existing -> {
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
          return table.playlistInfo()
              .saveAsync(
                  newPlaylist,
                  ifPlaylistVersionIs(existing.playlist_version)
              );
        });
  }

  private Expression ifPlaylistVersionIs(Long playlist_version) {
    return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(
            Map.of(":playlist_version", AttributeValue.builder().n("" + playlist_version).build()))
        .build();
  }
}
