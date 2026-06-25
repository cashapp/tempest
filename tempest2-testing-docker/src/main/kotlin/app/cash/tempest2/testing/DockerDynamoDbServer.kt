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
import app.cash.tempest2.testing.internal.hostName
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Ports
import com.google.common.util.concurrent.AbstractIdleService
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException

class DockerDynamoDbServer private constructor(
  override val port: Int,
  private val onBeforeStartup: () -> Unit,
  imageResolver: (String) -> String,
  authProvider: (() -> AuthConfig)?
) : AbstractIdleService(), TestDynamoDbServer {

  override val id = "tempest2-docker-dynamodb-local-$port"

  /** The image reference to pull and run, after applying [Factory.imageResolver]. */
  private val resolvedImage = imageResolver(DEFAULT_IMAGE)

  override fun startUp() {
    onBeforeStartup()
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
    Container(createCmd = {
        // DynamoDB Local listens on port 8000 by default.
        val exposedClientPort = ExposedPort.tcp(8000)
        val portBindings = Ports()
        portBindings.bind(exposedClientPort, Ports.Binding.bindPort(port))
        withImage(resolvedImage)
          .withName(id)
          .withExposedPorts(exposedClientPort)
          .withCmd("-jar", "DynamoDBLocal.jar", "-sharedDb", "-disableTelemetry")
          .withPortBindings(portBindings)
      },
      beforeStartHook = { _, _ -> },
      authConfig = authProvider?.let { it() },
    )
  )

  object Factory : TestDynamoDbServer.Factory<DockerDynamoDbServer> {
    /**
     * Hook to rewrite the Docker image reference before it is pulled and run. Receives [DEFAULT_IMAGE]
     * and returns the reference to actually use. Defaults to the identity function (pull from Docker
     * Hub). Override this to redirect pulls through a registry mirror, e.g.:
     *
     * ```
     * DockerDynamoDbServer.Factory.imageResolver = { image -> "my-mirror.example.com/$image" }
     * ```
     */
    var imageResolver: (String) -> String = { it }
    var authProvider: (() -> AuthConfig)? = null

    override fun hostName(port: Int): String = app.cash.tempest2.testing.internal.hostName(port)
    override fun create(port: Int, onBeforeStartup: () -> Unit) =
      DockerDynamoDbServer(port, onBeforeStartup, imageResolver, authProvider)
  }

  companion object {
    /** Default DynamoDB Local image, pulled from Docker Hub unless [Factory.imageResolver] rewrites it. */
    const val DEFAULT_IMAGE = "amazon/dynamodb-local:2.6.1"
  }
}
