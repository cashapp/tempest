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

package app.cash.tempest.testing

import app.cash.tempest.LogicalDb
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.google.common.util.concurrent.Service
import kotlin.reflect.KClass

interface TestDynamoDbClient : Service {
  val tables: List<TestTable>

  /** A DynamoDB instance that is usable while this service is running. */
  val dynamoDb: AmazonDynamoDB

  /** A DynamoDB streams instance that is usable while this service is running. */
  val dynamoDbStreams: AmazonDynamoDBStreams

  fun <DB : LogicalDb> logicalDb(type: KClass<DB>): DB {
    return logicalDb(type, DynamoDBMapperConfig.DEFAULT)
  }

  fun <DB : LogicalDb> logicalDb(type: KClass<DB>, mapperConfig: DynamoDBMapperConfig): DB {
    return LogicalDb.create(type, DynamoDBMapper(dynamoDb, mapperConfig))
  }

  fun <DB : LogicalDb> logicalDb(type: Class<DB>): DB {
    return logicalDb(type.kotlin, DynamoDBMapperConfig.DEFAULT)
  }

  fun <DB : LogicalDb> logicalDb(type: Class<DB>, mapperConfig: DynamoDBMapperConfig): DB {
    return logicalDb(type.kotlin, mapperConfig)
  }

  /** Cleans up tables in between test runs. */
  fun reset()
}

inline fun <reified DB : LogicalDb> TestDynamoDbClient.logicalDb(): DB {
  return logicalDb(DB::class)
}

inline fun <reified DB : LogicalDb> TestDynamoDbClient.logicalDb(mapperConfig: DynamoDBMapperConfig): DB {
  return logicalDb(DB::class, mapperConfig)
}
