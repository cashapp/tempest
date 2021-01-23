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

import app.cash.tempest.musiclibrary.java.PlaylistInfo;
import app.cash.tempest.musiclibrary.java.MusicTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;

public class OptimisticLocking {
  private final MusicTable table;

  public OptimisticLocking(MusicTable table) {
    this.table = table;
  }

  public void changePlaylistName(String playlistToken, String newName) {
    // Read.
    PlaylistInfo existing = table.playlistInfo().load(new PlaylistInfo.Key(playlistToken));
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
    table.playlistInfo().save(
        newPlaylist,
        ifPlaylistVersionIs(existing.playlist_version)
    );
  }

  private DynamoDBSaveExpression ifPlaylistVersionIs(Long playlist_version) {
    return new DynamoDBSaveExpression()
        .withExpectedEntry(
            "playlist_version",
            new ExpectedAttributeValue()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withN("" + playlist_version))
        );
  }
}
