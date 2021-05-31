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

import app.cash.tempest.testing.internal.DefaultTestDynamoDbClient
import app.cash.tempest.testing.internal.getLogger
import app.cash.tempest.testing.internal.pickRandomPort
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.ConcurrentHashMap

/**
 * This JUnit extension spins up a DynamoDB server in tests. It shares the server across tests and
 * keeps the server running until the process exits.
 */
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
      val port = port ?: DEFAULT_PORT
      return TestDynamoDb(
        DefaultTestDynamoDbClient(tables, port),
        serverFactory.create(port)
      )
    }
  }

  companion object {
    // Only pick random port once to share one test server with multiple tests.
    private val DEFAULT_PORT = pickRandomPort()
    private val runningServers = ConcurrentHashMap.newKeySet<String>()
    private val log = getLogger<TestDynamoDb>()
  }
}
