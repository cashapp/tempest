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

import app.cash.tempest2.BeginsWith;
import app.cash.tempest2.Between;
import app.cash.tempest2.Offset;
import app.cash.tempest2.Page;
import app.cash.tempest2.QueryConfig;
import app.cash.tempest2.musiclibrary.java.AlbumTrack;
import app.cash.tempest2.musiclibrary.java.MusicTable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class QueryNScan {

  private final MusicTable table;

  public QueryNScan(MusicTable table) {
    this.table = table;
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

  private Expression runLengthLongerThan(Duration duration) {
    return Expression.builder()
        .expression("run_length > :duration")
        .expressionValues(
            Map.of(":duration", AttributeValue.builder().s(duration.toString()).build()))
        .build();
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

  // Query - Specified Offset
  public List<AlbumTrack> loadAlbumTracksAfterTrack(String albumToken, String trackToken) {
    List<AlbumTrack> tracks = new ArrayList<>();
    Page<AlbumTrack.Key, AlbumTrack> page = null;
    Offset<AlbumTrack.Key> firstOffset = new Offset<>(new AlbumTrack.Key(albumToken, trackToken));

    do {
      page = table.albumTracks().query(
          // keyCondition.
          new BeginsWith<>(new AlbumTrack.Key(albumToken)),
          // config.
          new QueryConfig.Builder()
              .pageSize(10)
              .build(),
          // initialOffset.
          page != null ? page.getOffset() : firstOffset
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
  // Not supported.
}
