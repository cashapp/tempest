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

package app.cash.tempest.guides.java;

import app.cash.tempest.BatchWriteResult;
import app.cash.tempest.BatchWriteSet;
import app.cash.tempest.ItemSet;
import app.cash.tempest.musiclibrary.java.AlbumTrack;
import app.cash.tempest.musiclibrary.java.PlaylistInfo;
import app.cash.tempest.musiclibrary.java.MusicDb;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.DefaultBatchLoadRetryStrategy;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.DefaultBatchWriteRetryStrategy;
import java.util.List;

public class BatchReadWrite {
  private final MusicDb db;

  public BatchReadWrite(MusicDb db) {
    this.db = db;
  }

  // Batch Load.
  public List<AlbumTrack> loadPlaylistTracks(PlaylistInfo playlist) {
    ItemSet results = db.batchLoad(
        // keys.
        playlist.playlist_tracks, // [AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ...]
        // consistentReads.
        ConsistentReads.EVENTUAL,
        // retryStrategy.
        new DefaultBatchLoadRetryStrategy()
    );
    return results.getItems(AlbumTrack.class);
  }

  // Batch Write.
  public boolean backfill(
      List<AlbumTrack> albumTracksToSave,
      List<AlbumTrack.Key> albumTracksToDelete
  ) {
    BatchWriteSet writeSet = new BatchWriteSet.Builder()
        .clobber(albumTracksToSave)
        .delete(albumTracksToDelete)
        .build();
    BatchWriteResult result = db.batchWrite(
        writeSet,
        // retryStrategy.
        new DefaultBatchWriteRetryStrategy()
    );
    return result.isSuccessful();
  }
}
