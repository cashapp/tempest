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
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.google.common.util.concurrent.AbstractIdleService

class DefaultTestDynamoDbClient(
  override val tables: List<TestTable>,
  private val port: Int,
) : AbstractIdleService(), TestDynamoDbClient {

  override val dynamoDb: AmazonDynamoDB
    get() = requireNotNull(_dynamoDb) { "`dynamoDb` is only usable while the service is running" }
  override val dynamoDbStreams: AmazonDynamoDBStreams
    get() = requireNotNull(_dynamoDbStreams) { "`dynamoDbStreams` is only usable while the service is running" }

  private var _dynamoDb: AmazonDynamoDB? = null
  private var _dynamoDbStreams: AmazonDynamoDBStreams? = null

  override fun startUp() {
    _dynamoDb = connect(port)
    _dynamoDbStreams = connectToStreams(port)

    // Cleans up the tables before each run.
    for (tableName in dynamoDb.listTables().tableNames) {
      dynamoDb.deleteTable(tableName)
    }
    for (table in tables) {
      dynamoDb.createTable(table)
    }
  }

  override fun shutDown() {
    dynamoDb.shutdown()
    _dynamoDb = null
    dynamoDbStreams.shutdown()
    _dynamoDbStreams = null
  }
}
