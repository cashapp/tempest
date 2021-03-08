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
import org.junit.rules.ExternalResource
import java.util.concurrent.ConcurrentHashMap

/**
 * This JUnit rule spins up a DynamoDB server in tests. It shares the server across tests and
 * keeps the server running until the process exits.
 */
class TestDynamoDb private constructor(
  private val client: TestDynamoDbClient,
  private val server: TestDynamoDbServer
) : TestDynamoDbClient by client, ExternalResource() {

  override fun before() {
    server.startIfNeeded()
    client.startAsync()
    client.awaitRunning()
  }

  override fun after() {
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

    fun addTable(table: TestTable) = apply {
      tables.add(table)
    }

    fun addTables(tables: List<TestTable>) = apply {
      this.tables.addAll(tables)
    }

    fun build() = TestDynamoDb(
      DefaultTestDynamoDbClient(tables, DEFAULT_PORT),
      serverFactory.create(DEFAULT_PORT)
    )
  }

  companion object {
    private val DEFAULT_PORT = pickRandomPort()
    private val runningServers = ConcurrentHashMap.newKeySet<String>()
    private val log = getLogger<TestDynamoDb>()
  }
}
