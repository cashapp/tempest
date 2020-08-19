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

package app.cash.tempest.urlshortener.java;

import app.cash.tempest.urlshortener.UrlShortener;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RealUrlShortener implements UrlShortener {

  private final AliasTable table;

  public RealUrlShortener(AliasTable table) {
    this.table = table;
  }

  @Override public boolean shorten(@NotNull String shortUrl, @NotNull String destinationUrl) {
    Alias item = new Alias(shortUrl, destinationUrl);
    DynamoDBSaveExpression ifNotExist = new DynamoDBSaveExpression()
        .withExpectedEntry("short_url", new ExpectedAttributeValue()
            .withExists(false));
    try {
      table.aliases().save(item, ifNotExist);
      return true;
    } catch (ConditionalCheckFailedException e) {
      System.out.println("Failed to shorten $shortUrl because it already exists!");
      return false;
    }
  }

  @Nullable @Override public String redirect(@NotNull String shortUrl) {
    Alias.Key key = new Alias.Key(shortUrl);
    Alias alias = table.aliases().load(key);
    if (alias == null) {
      return null;
    }
    return alias.destination_url;
  }
}
