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
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

class DefaultTestDynamoDbClient(
  override val tables: List<TestTable>,
  private val port: Int,
) : AbstractIdleService(), TestDynamoDbClient {

  override val dynamoDb: DynamoDbClient
    get() = requireNotNull(_dynamoDb) { "`dynamoDb` is only usable while the service is running" }
  override val asyncDynamoDb: DynamoDbAsyncClient
    get() = requireNotNull(_dynamoDbAsync) { "`dynamoDb` is only usable while the service is running" }
  override val dynamoDbStreams: DynamoDbStreamsClient
    get() = requireNotNull(_dynamoDbStreams) { "`dynamoDbStreams` is only usable while the service is running" }
  override val asyncDynamoDbStreams: DynamoDbStreamsAsyncClient
    get() = requireNotNull(_dynamoDbStreamsAsync) { "`dynamoDbStreams` is only usable while the service is running" }

  private var _dynamoDb: DynamoDbClient? = null
  private var _dynamoDbAsync: DynamoDbAsyncClient? = null
  private var _dynamoDbStreams: DynamoDbStreamsClient? = null
  private var _dynamoDbStreamsAsync: DynamoDbStreamsAsyncClient? = null

  override fun startUp() {
    val hostName = hostName(port)
    _dynamoDb = connect(hostName, port)
    _dynamoDbAsync = connectAsync(hostName, port)
    _dynamoDbStreams = connectToStreams(hostName, port)
    _dynamoDbStreamsAsync = connectToStreamsAsync(hostName, port)

    // Cleans up the tables before each run.
    for (tableName in dynamoDb.listTables().tableNames()) {
      dynamoDb.deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
    }
    for (table in tables) {
      dynamoDb.createTable(table)
    }
  }

  override fun shutDown() {
    dynamoDb.close()
    _dynamoDb = null
    dynamoDbStreams.close()
    _dynamoDbStreams = null
  }
}
