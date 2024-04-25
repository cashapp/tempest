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
import app.cash.tempest2.TableNameResolver
import com.google.common.util.concurrent.Service
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import kotlin.reflect.KClass

typealias AsyncLogicalDb = app.cash.tempest2.AsyncLogicalDb

interface TestDynamoDbClient : Service {
  val tables: List<TestTable>

  /** A DynamoDB instance that is usable while this service is running. */
  val dynamoDb: DynamoDbClient

  /** A DynamoDB instance that is usable while this service is running. */
  val asyncDynamoDb: DynamoDbAsyncClient

  /** A DynamoDB streams instance that is usable while this service is running. */
  val dynamoDbStreams: DynamoDbStreamsClient

  /** A DynamoDB streams instance that is usable while this service is running. */
  val asyncDynamoDbStreams: DynamoDbStreamsAsyncClient

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
    return logicalDb(type, extensions, tableNameResolver = null)
  }

  fun <DB : LogicalDb> logicalDb(
    type: KClass<DB>,
    extensions: List<DynamoDbEnhancedClientExtension>,
    tableNameResolver: TableNameResolver? = null
  ): DB {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDb)
      .extensions(extensions)
      .build()
    return LogicalDb.create(type, enhancedClient, tableNameResolver)
  }

  fun <DB : LogicalDb> logicalDb(type: Class<DB>): DB {
    return logicalDb(type.kotlin)
  }

  fun <DB : LogicalDb> logicalDb(type: Class<DB>, vararg extensions: DynamoDbEnhancedClientExtension): DB {
    return logicalDb(type.kotlin, extensions.toList())
  }

  fun <DB : LogicalDb> logicalDb(
    type: Class<DB>,
    extensions: List<DynamoDbEnhancedClientExtension>
  ): DB {
    return logicalDb(type.kotlin, extensions)
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(type: KClass<DB>): DB {
    return asyncLogicalDb(type, emptyList())
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(type: KClass<DB>, vararg extensions: DynamoDbEnhancedClientExtension): DB {
    return asyncLogicalDb(type, extensions.toList())
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(
    type: KClass<DB>,
    extensions: List<DynamoDbEnhancedClientExtension>
  ): DB {
    return asyncLogicalDb(type, extensions, tableNameResolver = null)
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(
    type: KClass<DB>,
    extensions: List<DynamoDbEnhancedClientExtension>,
    tableNameResolver: TableNameResolver?
  ): DB {
    val enhancedClient = DynamoDbEnhancedAsyncClient.builder()
      .dynamoDbClient(asyncDynamoDb)
      .extensions(extensions)
      .build()
    return AsyncLogicalDb.create(type, enhancedClient)
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(type: Class<DB>): DB {
    return asyncLogicalDb(type.kotlin)
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(type: Class<DB>, vararg extensions: DynamoDbEnhancedClientExtension): DB {
    return asyncLogicalDb(type.kotlin, extensions.toList())
  }

  fun <DB : AsyncLogicalDb> asyncLogicalDb(
    type: Class<DB>,
    extensions: List<DynamoDbEnhancedClientExtension>
  ): DB {
    return asyncLogicalDb(type.kotlin, extensions)
  }
}

inline fun <reified DB : LogicalDb> TestDynamoDbClient.logicalDb(
  vararg extensions: DynamoDbEnhancedClientExtension,
  tableNameResolver: TableNameResolver? = null
): DB {
  return logicalDb(extensions.toList(), tableNameResolver)
}

inline fun <reified DB : LogicalDb> TestDynamoDbClient.logicalDb(
  extensions: List<DynamoDbEnhancedClientExtension>,
  tableNameResolver: TableNameResolver? = null
): DB {
  return logicalDb(DB::class, extensions, tableNameResolver)
}

inline fun <reified DB : AsyncLogicalDb> TestDynamoDbClient.asyncLogicalDb(
  vararg extensions: DynamoDbEnhancedClientExtension,
  tableNameResolver: TableNameResolver? = null
): DB {
  return asyncLogicalDb(extensions.toList(), tableNameResolver)
}

inline fun <reified DB : AsyncLogicalDb> TestDynamoDbClient.asyncLogicalDb(
  extensions: List<DynamoDbEnhancedClientExtension>,
  tableNameResolver: TableNameResolver? = null
): DB {
  return asyncLogicalDb(DB::class, extensions, tableNameResolver)
}
