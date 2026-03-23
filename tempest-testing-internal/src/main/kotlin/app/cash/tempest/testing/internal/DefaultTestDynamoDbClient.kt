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

package app.cash.tempest.testing.internal

import app.cash.tempest.testing.TestDynamoDbClient
import app.cash.tempest.testing.TestTable
import com.google.common.util.concurrent.AbstractIdleService

class DefaultTestDynamoDbClient(
  override val tables: List<TestTable>,
  private val port: Int,
) : AbstractIdleService(), TestDynamoDbClient {
  // Lazy so that hostName is resolved after the DynamoDB Local server is started,
  // not at construction time when the placeholder ServerSocket is still holding the port.
  private val hostName by lazy { hostName(port) }

  override val dynamoDb by lazy { buildDynamoDb(hostName, port) }
  override val dynamoDbStreams by lazy { buildDynamoDbStreams(hostName, port) }

  override fun startUp() {
    reset()
  }

  override fun shutDown() {
    dynamoDb.shutdown()
    dynamoDbStreams.shutdown()
  }

  override fun reset() {
    // Cleans up the tables before each run.
    log.info { "connecting to DynamoDB Local at $hostName:$port" }
    var lastException: Exception? = null
    for (attempt in 1..3) {
      try {
        val tableNames = dynamoDb.listTables().tableNames
        log.info { "successfully connected to DynamoDB Local at $hostName:$port" }
        for (tableName in tableNames) {
          dynamoDb.deleteTable(tableName)
        }
        for (table in tables) {
          dynamoDb.createTable(table)
        }
        return
      } catch (e: Exception) {
        lastException = e
        log.warn(e) { "failed to connect to DynamoDB Local at $hostName:$port (attempt $attempt/3), retrying..." }
        if (attempt < 3) {
          Thread.sleep(500)
        }
      }
    }
    log.error(lastException!!) { "failed to connect to DynamoDB Local at $hostName:$port after 3 attempts" }
    throw lastException
  }

  companion object {
    private val log = getLogger<DefaultTestDynamoDbClient>()
  }
}
