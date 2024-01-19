/*
 * Copyright 2021 Square Inc.
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

package app.cash.tempest2.guides.java;

import app.cash.tempest2.ItemSet;
import app.cash.tempest2.TransactionWriteSet;
import app.cash.tempest2.WritingPager;
import app.cash.tempest2.musiclibrary.java.AlbumTrack;
import app.cash.tempest2.musiclibrary.java.MusicDb;
import app.cash.tempest2.musiclibrary.java.MusicTable;
import app.cash.tempest2.musiclibrary.java.PlaylistInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import static app.cash.tempest2.guides.java.Transaction.ifPlaylistVersionIs;
import static app.cash.tempest2.guides.java.Transaction.trackExists;

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

  static Expression ifPlaylistVersionIs(Long playlist_version) {
    return Expression.builder()
        .expression("playlist_version = :playlist_version")
        .expressionValues(
            Map.of(":playlist_version", AttributeValue.builder().n("" + playlist_version).build()))
        .build();
  }

  static Expression trackExists() {
    return Expression.builder()
        .expression("attribute_exists(track_title)")
        .build();
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

  @Override public void pageWritten(@NotNull TransactionWriteSet writeSet) {
    // no-op
  }
}
