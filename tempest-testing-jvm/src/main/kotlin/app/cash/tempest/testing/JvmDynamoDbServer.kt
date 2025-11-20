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

import app.cash.tempest.testing.dynamodb.local.shaded.com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import app.cash.tempest.testing.dynamodb.local.shaded.com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer
import com.google.common.util.concurrent.AbstractIdleService

class JvmDynamoDbServer private constructor(
  override val port: Int,
  private val onBeforeStartup: () -> Unit
) : AbstractIdleService(), TestDynamoDbServer {

  override val id = "tempest-jvm-dynamodb-local-$port"

  private lateinit var server: DynamoDBProxyServer

  override fun startUp() {
    onBeforeStartup()
    try {
      server = ServerRunner.createServerFromCommandLineArgs(
        arrayOf("-inMemory", "-disableTelemetry", "-port", port.toString())
      )
      server.start()
    } catch (e: Exception) {
      log.error(e) { "Failed to start DynamoDB Local server on port $port" }
      throw e
    }
  }

  companion object {
    private val log = app.cash.tempest.testing.internal.getLogger<JvmDynamoDbServer>()
  }

  override fun shutDown() {
    server.stop()
  }

  object Factory : TestDynamoDbServer.Factory<JvmDynamoDbServer> {
    override fun create(port: Int, onBeforeStartup: () -> Unit) = JvmDynamoDbServer(port, onBeforeStartup)
  }
}
