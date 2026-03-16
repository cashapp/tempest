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

import app.cash.tempest.testing.dynamodb.local.shaded.com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import app.cash.tempest.testing.dynamodb.local.shaded.com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer
import app.cash.tempest2.testing.internal.getLogger
import app.cash.tempest2.testing.internal.isServerListening
import com.google.common.util.concurrent.AbstractIdleService

class JvmDynamoDbServer private constructor(
  override val port: Int,
  private val onBeforeStartup: () -> Unit
) : AbstractIdleService(), TestDynamoDbServer {

  override val id = "tempest2-jvm-dynamodb-local-$port"

  private lateinit var server: DynamoDBProxyServer

  override fun startUp() {
    log.info { "releasing port $port for $id" }
    onBeforeStartup()
    try {
      log.info { "starting DynamoDB Local server on port $port for $id" }
      server = ServerRunner.createServerFromCommandLineArgs(
        arrayOf("-inMemory", "-disableTelemetry", "-port", port.toString())
      )
      server.start()
      log.info { "DynamoDB Local server started on port $port for $id" }
    } catch (e: Exception) {
      log.error(e) { "failed to start DynamoDB Local server on port $port for $id" }
      throw e
    }

    // Health check: verify the server is actually listening
    var serverReady = false
    for (attempt in 1..5) {
      if (isServerListening("localhost", port)) {
        log.info { "health check passed for $id on port $port (attempt $attempt/5)" }
        serverReady = true
        break
      }
      log.info { "health check attempt $attempt/5 for $id on port $port - server not yet listening" }
      if (attempt < 5) {
        Thread.sleep(200)
      }
    }
    if (!serverReady) {
      log.warn { "health check failed after 5 attempts for $id on port $port - server may not be ready" }
    }
  }

  companion object {
    private val log = getLogger<JvmDynamoDbServer>()
  }

  override fun shutDown() {
    server.stop()
  }

  object Factory : TestDynamoDbServer.Factory<JvmDynamoDbServer> {
    override fun create(port: Int, onBeforeStartup: () -> Unit) = JvmDynamoDbServer(port, onBeforeStartup)
  }
}
