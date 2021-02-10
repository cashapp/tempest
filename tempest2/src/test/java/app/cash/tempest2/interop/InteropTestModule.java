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

package app.cash.tempest2.interop;

import app.cash.tempest2.LogicalDb;
import app.cash.tempest2.urlshortener.java.AliasDb;
import app.cash.tempest2.urlshortener.java.AliasItem;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.inject.Singleton;
import kotlin.jvm.internal.Reflection;
import misk.MiskTestingServiceModule;
import misk.aws2.dynamodb.testing.DynamoDbTable;
import misk.aws2.dynamodb.testing.InProcessDynamoDbModule;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class InteropTestModule extends AbstractModule {

  @Override protected void configure() {
    install(new MiskTestingServiceModule());
    install(
        new InProcessDynamoDbModule(
            new DynamoDbTable(
                "j_alias_items",
                Reflection.createKotlinClass(AliasItem.class)
            )
        )
    );
  }

  @Provides
  @Singleton
  AliasDb provideJAliasDb(DynamoDbClient dynamoDbClient) {
    var dynamoDbEnhancedClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        .build();
    return LogicalDb.create(AliasDb.class, dynamoDbEnhancedClient);
  }
}
