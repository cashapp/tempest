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

package app.cash.tempest2.urlshortener.java;

import app.cash.tempest2.LogicalDb;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

public class Main {

  public static void main(String[] args) {
    DynamoDbEnhancedClient client = DynamoDbEnhancedClient.create();
    AliasDb db = LogicalDb.create(AliasDb.class, client);
    UrlShortener urlShortener = new RealUrlShortener(db.aliasTable());
    urlShortener.shorten("tempest", "https://cashapp.github.io/tempest");
  }
}
