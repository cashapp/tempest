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

package app.cash.tempest2.testing.internal

import app.cash.tempest2.testing.TestDynamoDbClient
import app.cash.tempest2.testing.TestTable
import com.google.common.util.concurrent.AbstractIdleService
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest

class DefaultTestDynamoDbClient(
  override val tables: List<TestTable>,
  port: Int,
) : AbstractIdleService(), TestDynamoDbClient {
  // TODO: Is there a better way of doing this than making a network connection?
  private val hostName by lazy { hostName(port) }

  override val dynamoDb = buildDynamoDb(hostName, port)
  override val asyncDynamoDb = buildAsyncDynamoDb(hostName, port)
  override val dynamoDbStreams = buildDynamoDbStreams(hostName, port)
  override val asyncDynamoDbStreams = buildAsyncDynamoDbStreams(hostName, port)

  override fun startUp() {
    reset()
  }

  override fun shutDown() {
    dynamoDb.close()
    dynamoDbStreams.close()
  }

  override fun reset() {
    // Cleans up the tables before each run.
    for (tableName in dynamoDb.listTables().tableNames()) {
      dynamoDb.deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
    }
    for (table in tables) {
      dynamoDb.createTable(table)
    }
  }
}
