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

import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import kotlin.reflect.KClass

/**
 * Use this with [TestDynamoDbClient] to configure your DynamoDB
 * tables for each test execution.
 *
 * Use [configureTable] to customize the table creation request for testing, such as to configure
 * the secondary indexes required by `ProjectionType.ALL`.
 */
data class TestTable internal constructor(
  val tableClass: KClass<*>,
  val configureTable: (CreateTableRequest) -> CreateTableRequest = { it }
) {
  companion object {
    inline fun <reified T> create(
      noinline configureTable: (CreateTableRequest) -> CreateTableRequest = { it }
    ) = create(T::class, configureTable)

    fun create(
      tableClass: KClass<*>,
      configureTable: (CreateTableRequest) -> CreateTableRequest = { it }
    ) = TestTable(tableClass, configureTable)

    @JvmStatic
    @JvmOverloads
    fun create(
      tableClass: Class<*>,
      configureTable: (CreateTableRequest) -> CreateTableRequest = { it }
    ) = create(tableClass.kotlin, configureTable)
  }
}
