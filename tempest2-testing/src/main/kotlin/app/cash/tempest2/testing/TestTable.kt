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

import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest
import kotlin.reflect.KClass

/**
 * Use this with [TestDynamoDbClient] to configure your DynamoDB
 * tables for each test execution.
 *
 * Use [configureTable] to customize the table creation request for testing, such as to configure
 * the secondary indexes required by `ProjectionType.ALL`.
 */
data class TestTable internal constructor(
  val tableName: String,
  val tableClass: KClass<*>,
  val configureTable: (CreateTableEnhancedRequest) -> CreateTableEnhancedRequest = { it }
) {
  companion object {
    inline fun <reified T> create(
      tableName: String,
      noinline configureTable: (CreateTableEnhancedRequest) -> CreateTableEnhancedRequest = { it }
    ) = create(tableName, T::class, configureTable)

    fun create(
      tableName: String,
      tableClass: KClass<*>,
      configureTable: (CreateTableEnhancedRequest) -> CreateTableEnhancedRequest = { it }
    ) = TestTable(tableName, tableClass, configureTable)

    @JvmStatic
    @JvmOverloads
    fun create(
      tableName: String,
      tableClass: Class<*>,
      configureTable: (CreateTableEnhancedRequest) -> CreateTableEnhancedRequest = { it }
    ) = create(tableName, tableClass.kotlin, configureTable)
  }
}
