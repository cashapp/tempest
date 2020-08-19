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

import app.cash.tempest.BeginsWith;
import app.cash.tempest.Between;
import app.cash.tempest.FilterExpression;
import app.cash.tempest.Page;
import app.cash.tempest.QueryConfig;
import app.cash.tempest.ScanConfig;
import app.cash.tempest.WorkerId;
import app.cash.tempest.musiclibrary.java.AlbumTrack;
import app.cash.tempest.musiclibrary.java.MusicTable;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class QueryNScan {

  private final MusicTable table;
  private final ExecutorService executor;

  public QueryNScan(MusicTable table, ExecutorService executor) {
    this.table = table;
    this.executor = executor;
  }

  // Query - Key Condition - Partition Key and Entity Type.
  public List<AlbumTrack> loadAlbumTracks(String albumToken) {
    Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
        // keyCondition.
        new BeginsWith<>(
            // prefix.
            new AlbumTrack.Key(albumToken)
        )
    );
    return page.getContents();
  }

  // Query - Key Condition - Partition Key and Sort Key Prefix.
  public List<AlbumTrack> loadAlbumTracks2(String albumToken) {
    Page<AlbumTrack.TitleIndexOffset, AlbumTrack> page = table.albumTracksByTitle().query(
        // keyCondition.
        new BeginsWith<>(
            // prefix.
            new AlbumTrack.TitleIndexOffset(albumToken, "I want ")
        )
    );
    return page.getContents();
  }

  // Query - Key Condition - Partition Key and Sort Key Range.
  public List<AlbumTrack> loadAlbumTracks3(String albumToken) {
    Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
        // keyCondition.
        new Between<>(
            // startInclusive.
            new AlbumTrack.Key(albumToken, /* track_number */ 5L),
            // endInclusive.
            new AlbumTrack.Key(albumToken, /* track_number */ 9L))
    );
    return page.getContents();
  }

  // Query - Descending Order.
  public List<AlbumTrack> loadAlbumTracks4(String albumToken) {
    Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
        // keyCondition.
        new BeginsWith<>(
            // prefix.
            new AlbumTrack.Key(albumToken)
        ),
        // config.
        new QueryConfig.Builder()
            .asc(false)
            .build()
    );
    return page.getContents();
  }

  // Query - Filter Expression
  public List<AlbumTrack> loadAlbumTracks5(String albumToken) {
    Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().query(
        // keyCondition.
        new BeginsWith<>(
            // prefix.
            new AlbumTrack.Key(albumToken)
        ),
        // config.
        new QueryConfig.Builder()
            .filterExpression(runLengthLongerThan(Duration.ofMinutes(3)))
            .build()
    );
    return page.getContents();
  }

  private FilterExpression runLengthLongerThan(Duration duration) {
    HashMap<String, AttributeValue> attributeValues = new HashMap<>();
    attributeValues.put(":duration", new AttributeValue().withS(duration.toString()));
    return new FilterExpression(
        "run_length > :duration",
        attributeValues
    );
  }

  // Query - Pagination.
  public List<AlbumTrack> loadAlbumTracks6(String albumToken) {
    List<AlbumTrack> tracks = new ArrayList<>();
    Page<AlbumTrack.Key, AlbumTrack> page = null;
    do {
      page = table.albumTracks().query(
          // keyCondition.
          new BeginsWith<>(new AlbumTrack.Key(albumToken)),
          // config.
          new QueryConfig.Builder()
              .pageSize(10)
              .build(),
          // initialOffset.
          page != null ? page.getOffset() : null
      );
      tracks.addAll(page.getContents());
    } while (page.getHasMorePages());
    return tracks;
  }

  // Scan.
  public List<AlbumTrack> loadAllAlbumTracks() {
    Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().scan();
    return page.getContents();
  }

  // Scan - Parallel.
  public List<AlbumTrack> loadAllAlbumTracks2() {
    Future<List<AlbumTrack>> segment1 = executor.submit(() -> loadSegment(1));
    Future<List<AlbumTrack>> segment2 = executor.submit(() -> loadSegment(2));
    List<AlbumTrack> results = new ArrayList<>();
    try {
      results.addAll(segment1.get());
      results.addAll(segment2.get());
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to load tracks", e);
    }
    return results;
  }

  private List<AlbumTrack> loadSegment(int segment) {
    Page<AlbumTrack.Key, AlbumTrack> page = table.albumTracks().scan(
        new ScanConfig.Builder()
            .workerId(new WorkerId(segment, /* totalSegments */ 2))
            .build()
    );
    return page.getContents();
  }
}
