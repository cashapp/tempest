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

import app.cash.tempest2.testing.internal.TestDynamoDbService
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * This JUnit extension spins up a DynamoDB server in tests. It shares the server across tests and
 * keeps the server running until the process exits.
 */
class TestDynamoDb private constructor(
  private val service: TestDynamoDbService,
) : TestDynamoDbClient by service.client, BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    service.startAsync()
    service.awaitRunning()
  }

  override fun afterEach(context: ExtensionContext?) {
    service.stopAsync()
    service.awaitTerminated()
  }

  class Builder(
    private val serverFactory: TestDynamoDbServer.Factory<*>
  ) {
    private val tables = mutableListOf<TestTable>()
    private var port: Int? = null

    fun addTable(table: TestTable) = apply {
      tables.add(table)
    }

    fun addTables(tables: List<TestTable>) = apply {
      this.tables.addAll(tables)
    }

    fun port(port: Int) = apply {
      this.port = port
    }

    fun build(): TestDynamoDb {
      return TestDynamoDb(
        TestDynamoDbService.create(serverFactory, tables, port)
      )
    }
  }
}
