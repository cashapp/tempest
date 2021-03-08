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

import app.cash.tempest.javarecord.urlshortener.Alias;
import app.cash.tempest.javarecord.urlshortener.AliasDb;
import app.cash.tempest.javarecord.urlshortener.AliasItem;
import app.cash.tempest.javarecord.urlshortener.AliasTable;
import app.cash.tempest.testing.JvmDynamoDbServer;
import app.cash.tempest.testing.TestDynamoDb;
import app.cash.tempest.testing.TestTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaRecordTest {
  @RegisterExtension
  TestDynamoDb db = testDb();

  @Test
  public void javaLogicalTypeJavaItemType() {
    Alias alias = new Alias(
        "SquareCLA",
        "https://docs.google.com/forms/d/e/1FAIpQLSeRVQ35-gq2vdSxD1kdh7CJwRdjmUA0EZ9gRXaWYoUeKPZEQQ/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1"
    );
    aliasTable().aliases().save(alias);
    Alias loadedAlias = aliasTable().aliases().load(alias.key());
    assertThat(loadedAlias).isNotNull();
    assertThat(loadedAlias.short_url()).isEqualTo(alias.short_url());
    assertThat(loadedAlias.destination_url()).isEqualTo(alias.destination_url());
  }

  private static TestDynamoDb testDb() {
    return new TestDynamoDb.Builder(JvmDynamoDbServer.Factory.INSTANCE)
        .addTable(TestTable.create(AliasItem.class))
        .build();
  }

  private AliasTable aliasTable() {
    return db.logicalDb(AliasDb.class).aliasTable();
  }
}
