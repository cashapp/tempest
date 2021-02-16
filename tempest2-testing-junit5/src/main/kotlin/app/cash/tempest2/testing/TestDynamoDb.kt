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

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.ConcurrentHashMap

class TestDynamoDb private constructor(
  private val client: TestDynamoDbClient,
  private val server: TestDynamoDbServer,
) : TestDynamoDbClient by client, BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    server.startIfNeeded()
    client.startAsync()
    client.awaitRunning()
  }

  override fun afterEach(context: ExtensionContext?) {
    client.stopAsync()
    client.awaitTerminated()
  }

  private fun TestDynamoDbServer.startIfNeeded() {
    if (runningServers.contains(id)) {
      log.info { "$id already running, not starting anything" }
      return
    }
    log.info { "starting $id" }
    startAsync()
    awaitRunning()
    Runtime.getRuntime().addShutdownHook(
      Thread {
        log.info { "stopping $id" }
        stopAsync()
        awaitTerminated()
      }
    )
    runningServers.add(id)
  }

  class Builder(
    private val server: TestDynamoDbServer
  ) {
    private val tables = mutableListOf<TestTable>()

    fun addTable(table: TestTable) = apply {
      tables.add(table)
    }

    fun addTables(tables: List<TestTable>) = apply {
      this.tables.addAll(tables)
    }

    fun build() = TestDynamoDb(DefaultTestDynamoDbClient(tables), server)
  }

  companion object {
    private val runningServers = ConcurrentHashMap.newKeySet<String>()
    private val log = getLogger<TestDynamoDb>()
  }
}
