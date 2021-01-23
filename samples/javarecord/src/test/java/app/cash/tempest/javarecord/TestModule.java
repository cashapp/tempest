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

package app.cash.tempest.javarecord;

import app.cash.tempest.LogicalDb;
import app.cash.tempest.javarecord.urlshortener.AliasDb;
import app.cash.tempest.javarecord.urlshortener.AliasItem;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import kotlin.jvm.internal.Reflection;
import misk.MiskTestingServiceModule;
import misk.aws.dynamodb.testing.DynamoDbTable;
import misk.aws.dynamodb.testing.InProcessDynamoDbModule;

public class TestModule extends AbstractModule {

  @Override protected void configure() {
    install(new MiskTestingServiceModule());
    install(
        new InProcessDynamoDbModule(
            new DynamoDbTable(
                Reflection.createKotlinClass(AliasItem.class),
                (createTableRequest) -> createTableRequest
            )
        )
    );
  }

  @Provides
  @Singleton
  AliasDb provideJAliasDb(AmazonDynamoDB amazonDynamoDB) {
    var dynamoDbMapper = new DynamoDBMapper(amazonDynamoDB);
    return LogicalDb.create(AliasDb.class, dynamoDbMapper);
  }
}
