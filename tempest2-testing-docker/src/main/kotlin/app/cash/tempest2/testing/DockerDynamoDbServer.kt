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

import app.cash.tempest2.testing.internal.buildDynamoDb
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import com.google.common.util.concurrent.AbstractIdleService
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException

class DockerDynamoDbServer private constructor(
  override val port: Int
) : AbstractIdleService(), TestDynamoDbServer {

  override val id = "tempest2-docker-dynamodb-local-$port"

  override fun startUp() {
    composer.start()

    // Temporary client to block until the container is running
    val client = buildDynamoDb(port)
    while (true) {
      try {
        client.deleteTable(DeleteTableRequest.builder().tableName("not a table").build())
      } catch (e: Exception) {
        if (e is DynamoDbException) {
          break
        }
        Thread.sleep(100)
      }
    }
    client.close()
  }

  override fun shutDown() {
    composer.stop()
  }

  private val composer = Composer(
    "e-$id",
    Container {
      // DynamoDB Local listens on port 8000 by default.
      val exposedClientPort = ExposedPort.tcp(8000)
      val portBindings = Ports()
      portBindings.bind(exposedClientPort, Ports.Binding.bindPort(port))
      withImage("amazon/dynamodb-local")
        .withName(id)
        .withExposedPorts(exposedClientPort)
        .withCmd("-jar", "DynamoDBLocal.jar", "-sharedDb")
        .withPortBindings(portBindings)
    }
  )

  object Factory : TestDynamoDbServer.Factory<DockerDynamoDbServer> {
    override fun create(port: Int) = DockerDynamoDbServer(port)
  }
}
