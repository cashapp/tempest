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

import app.cash.tempest.musiclibrary.java.AlbumInfo;
import app.cash.tempest.musiclibrary.java.MusicTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import java.time.LocalDate;
import javax.annotation.Nullable;

public class Crud {
  private final MusicTable table;

  public Crud(MusicTable table) {
    this.table = table;
  }

  // Read.
  @Nullable
  public String getAlbumTitle(String albumToken) {
    AlbumInfo albumInfo = table.albumInfo().load(new AlbumInfo.Key(albumToken));
    if (albumInfo == null) {
      return null;
    }
    return albumInfo.album_title;
  }

  // Read - Eventual consistency.
  public void readAfterWrite() {
    // Write an item.
    AlbumInfo item = new AlbumInfo(
        // album_token.
        "ALBUM_cafcf892",
        // album_title.
        "The Dark Side of the Moon",
        // artist_name.
        "Pink Floyd",
        // release_date.
        LocalDate.of(1973, 3, 1),
        // genre_name.
        "Progressive rock"
    );
    table.albumInfo().save(item);
    // Read that item.
    AlbumInfo itemRead = table.albumInfo().load(item.key);
    // Note that the value we just read might be older than the value we wrote.
  }

  // Read - Strongly consistent.
  @Nullable
  public String getAlbumTitle2(String albumToken) {
    AlbumInfo albumInfo = table.albumInfo().load(
        new AlbumInfo.Key(albumToken),
        // consistentReads.
        ConsistentReads.CONSISTENT);
    if (albumInfo == null) {
      return null;
    }
    return albumInfo.album_title;
  }

  // Update.
  public void addAlbum(AlbumInfo albumInfo) {
    table.albumInfo().save(albumInfo);
  }

  // Update - Conditional.
  public void addAlbum2(AlbumInfo albumInfo) {
    table.albumInfo().save(albumInfo, ifNotExist());
  }

  private DynamoDBSaveExpression ifNotExist() {
    return new DynamoDBSaveExpression()
        .withExpectedEntry("partition_key", new ExpectedAttributeValue().withExists(false));
  }

  // Delete.
  public void deleteAlbum(String albumToken) {
    table.albumInfo().deleteKey(new AlbumInfo.Key(albumToken));
  }

  // Delete - Conditional.
  public void deleteAlbum2(String albumToken) {
    table.albumInfo().deleteKey(new AlbumInfo.Key(albumToken), ifExist());
  }

  private DynamoDBDeleteExpression ifExist() {
    return new DynamoDBDeleteExpression()
        .withExpectedEntry("partition_key", new ExpectedAttributeValue().withExists(true));
  }
}
