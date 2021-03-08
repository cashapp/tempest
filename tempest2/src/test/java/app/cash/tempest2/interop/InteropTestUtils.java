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

import app.cash.tempest2.testing.JvmDynamoDbServer;
import app.cash.tempest2.testing.TestDynamoDb;
import app.cash.tempest2.testing.TestTable;
import app.cash.tempest2.urlshortener.java.AliasItem;

public class InteropTestUtils {

  public static TestDynamoDb testDb() {
    return new TestDynamoDb.Builder(JvmDynamoDbServer.Factory.INSTANCE)
        .addTable(TestTable.create("j_alias_items", AliasItem.class))
        .build();
  }
}
