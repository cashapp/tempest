/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.tempest.guides.java;

import app.cash.tempest.ItemSet;
import app.cash.tempest.TransactionWriteSet;
import app.cash.tempest.WritingPager;
import app.cash.tempest.musiclibrary.java.AlbumTrack;
import app.cash.tempest.musiclibrary.java.MusicDb;
import app.cash.tempest.musiclibrary.java.MusicTable;
import app.cash.tempest.musiclibrary.java.PlaylistInfo;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import static app.cash.tempest.guides.java.Transaction.ifPlaylistVersionIs;
import static app.cash.tempest.guides.java.Transaction.trackExists;

public class Transaction {

  private final MusicDb db;
  private final MusicTable table;

  public Transaction(MusicDb db) {
    this.db = db;
    table = db.music();
  }

  // Transactional Read.
  public List<AlbumTrack> loadPlaylistTracks(PlaylistInfo playlist) {
    ItemSet results = db.transactionLoad(
        playlist.playlist_tracks // [ AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ... ]
    );
    return results.getItems(AlbumTrack.class);
  }

  // Transactional Write.
  public void addTrackToPlaylist(
      String playlistToken,
      AlbumTrack.Key albumTrack
  ) {
    // Read.
    PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
    if (existing == null) {
      throw new IllegalStateException("Playlist does not exist: " + playlistToken);
    }
    // Modify.
    List<AlbumTrack.Key> playlistTrackTokens = new ArrayList<>(existing.playlist_tracks);
    playlistTrackTokens.add(albumTrack);
    PlaylistInfo newPlaylist = new PlaylistInfo(
        existing.playlist_token,
        existing.playlist_name,
        // playlist_tracks.
        playlistTrackTokens,
        // playlist_version.
        existing.playlist_version + 1
    );
    // Write.
    TransactionWriteSet writeSet = new TransactionWriteSet.Builder()
        .save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
        // Add a playlist entry only if the album track exists.
        .checkCondition(albumTrack, trackExists())
        .build();
    db.transactionWrite(writeSet);
  }

  // Transactional Write - Writing Pager.
  public void addTracksToPlaylist(
      String playlistToken,
      List<AlbumTrack.Key> albumTracks
  ) {
    new WritingPager<>(
        db,
        albumTracks,
        // maxTransactionItems.
        25,
        // handler.
        new AlbumTrackWritingPagerHandler(playlistToken, table)
    ).execute();
  }

  static DynamoDBTransactionWriteExpression ifPlaylistVersionIs(Long playlist_version) {
    HashMap<String, AttributeValue> attributeValues = new HashMap<>();
    attributeValues.put(":playlist_version", new AttributeValue().withN("$playlist_version"));
    return new DynamoDBTransactionWriteExpression()
        .withConditionExpression("playlist_version = :playlist_version")
        .withExpressionAttributeValues(attributeValues);
  }

  static DynamoDBTransactionWriteExpression trackExists() {
    return new DynamoDBTransactionWriteExpression()
        .withConditionExpression("attribute_exists(track_title)");
  }
}

class AlbumTrackWritingPagerHandler implements WritingPager.Handler<AlbumTrack.Key> {
  private final String playlistToken;
  private final MusicTable table;
  private PlaylistInfo currentPagePlaylistInfo;
  private List<AlbumTrack.Key> currentPageTracks;

  AlbumTrackWritingPagerHandler(String playlistToken,
      MusicTable table) {
    this.playlistToken = playlistToken;
    this.table = table;
  }

  @Override public void eachPage(Function0<Unit> proceed) {
    proceed.invoke();
  }

  @Override public int beforePage(List<AlbumTrack.Key> remainingUpdates,
      int maxTransactionItems) {
    // Reserve 1 for the playlist info at the end.
    currentPageTracks = remainingUpdates.subList(0, maxTransactionItems - 1);
    currentPagePlaylistInfo = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
    return currentPageTracks.size();
  }

  @Override public void item(TransactionWriteSet.Builder builder, AlbumTrack.Key item) {
    builder.checkCondition(item, trackExists());
  }

  @Override public void finishPage(TransactionWriteSet.Builder builder) {
    PlaylistInfo existing = currentPagePlaylistInfo;
    List<AlbumTrack.Key> playlistTrackTokens = new ArrayList<>();
    playlistTrackTokens.addAll(existing.playlist_tracks);
    playlistTrackTokens.addAll(currentPageTracks);
    PlaylistInfo newPlaylist = new PlaylistInfo(
        existing.playlist_token,
        existing.playlist_name,
        // playlist_tracks.
        playlistTrackTokens,
        // playlist_version.
        existing.playlist_version + 1
    );
    builder.save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version));
  }
}
