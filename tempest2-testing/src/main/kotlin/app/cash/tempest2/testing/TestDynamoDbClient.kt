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

package app.cash.tempest2.testing

import app.cash.tempest2.LogicalDb
import com.google.common.util.concurrent.Service
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import kotlin.reflect.KClass

interface TestDynamoDbClient : Service {
  val tables: List<TestTable>

  /** A DynamoDB instance that is usable while this service is running. */
  val dynamoDb: DynamoDbClient

  /** A DynamoDB streams instance that is usable while this service is running. */
  val dynamoDbStreams: DynamoDbStreamsClient

  fun <DB : LogicalDb> logicalDb(type: KClass<DB>): DB {
    return logicalDb(type, emptyList())
  }

  fun <DB : LogicalDb> logicalDb(type: KClass<DB>, vararg extensions: DynamoDbEnhancedClientExtension): DB {
    return logicalDb(type, extensions.toList())
  }

  fun <DB : LogicalDb> logicalDb(
    type: KClass<DB>,
    extensions: List<DynamoDbEnhancedClientExtension>
  ): DB {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDb)
      .extensions(extensions)
      .build()
    return LogicalDb.create(type, enhancedClient)
  }
}

inline fun <reified DB : LogicalDb> TestDynamoDbClient.logicalDb(vararg extensions: DynamoDbEnhancedClientExtension): DB {
  return logicalDb(extensions.toList())
}

inline fun <reified DB : LogicalDb> TestDynamoDbClient.logicalDb(extensions: List<DynamoDbEnhancedClientExtension>): DB {
  return logicalDb(DB::class, extensions)
}
