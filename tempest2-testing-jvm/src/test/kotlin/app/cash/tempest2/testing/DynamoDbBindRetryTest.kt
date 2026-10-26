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
import java.net.BindException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DynamoDbBindRetryTest {
  @Test
  fun `clients obtained before startup follow the new port and later services reuse it`() {
    val factory = CollidingFactory(collisions = 1)
    val first = TestDynamoDbService.create(factory, emptyList())
    val second = TestDynamoDbService.create(factory, emptyList())
    val initialPort = first.server.port
    // Misk binds these SDK instances before starting the service graph.
    val dynamoDb = first.client.dynamoDb
    val asyncDynamoDb = first.client.asyncDynamoDb
    val streams = first.client.dynamoDbStreams
    val asyncStreams = first.client.asyncDynamoDbStreams
    try {
      first.startAsync().awaitRunning(30, SECONDS)
      assertThat(first.server.port).isNotEqualTo(initialPort)
      assertThat(dynamoDb.listTables().tableNames()).isEmpty()
      assertThat(asyncDynamoDb.listTables().get(10, SECONDS).tableNames()).isEmpty()
      assertThat(streams.listStreams().streams()).isEmpty()
      assertThat(asyncStreams.listStreams().get(10, SECONDS).streams()).isEmpty()
      second.startAsync().awaitRunning(30, SECONDS)
      assertThat(second.server).isSameAs(first.server)
      assertThat(factory.attempts).isEqualTo(2)
    } finally {
      close(first)
      close(second)
      first.server.stopAsync().awaitTerminated(10, SECONDS)
      factory.close()
    }
  }

  @Test
  fun `bind retries stop after three attempts and retain previous failures`() {
    val factory = CollidingFactory(collisions = 3)
    val service = TestDynamoDbService.create(factory, emptyList())
    try {
      assertThatThrownBy { service.startAsync().awaitRunning(30, SECONDS) }
        .hasRootCauseInstanceOf(BindException::class.java)
      assertThat(factory.attempts).isEqualTo(3)
      assertThat(service.server.failureCause().suppressed).hasSize(2)
    } finally {
      close(service)
      factory.close()
    }
  }

  @Test
  fun `an explicitly requested port is not replaced`() {
    val factory = CollidingFactory(collisions = 1)
    val port = ServerSocket(0).use { it.localPort }
    val service = TestDynamoDbService.create(factory, emptyList(), port)
    try {
      assertThatThrownBy { service.startAsync().awaitRunning(30, SECONDS) }
        .hasRootCauseInstanceOf(BindException::class.java)
      assertThat(service.server.port).isEqualTo(port)
      assertThat(factory.attempts).isEqualTo(1)
    } finally {
      close(service)
      factory.close()
    }
  }

  @Test
  fun `non-bind startup failures are not retried`() {
    var attempts = 0
    val factory = object : TestDynamoDbServer.Factory<JvmDynamoDbServer> {
      override fun hostName(port: Int) = "localhost"
      override fun create(port: Int, onBeforeStartup: () -> Unit) =
        JvmDynamoDbServer.Factory.create(port) {
          onBeforeStartup()
          attempts++
          throw IllegalArgumentException("invalid configuration")
        }
    }
    val service = TestDynamoDbService.create(factory, emptyList())
    try {
      assertThatThrownBy { service.startAsync().awaitRunning(30, SECONDS) }
        .hasRootCauseMessage("invalid configuration")
      assertThat(attempts).isEqualTo(1)
    } finally {
      close(service)
    }
  }

  private class CollidingFactory(private val collisions: Int) : TestDynamoDbServer.Factory<JvmDynamoDbServer> {
    var attempts = 0
    private val blockers = mutableListOf<ServerSocket>()
    override fun hostName(port: Int) = "localhost"
    override fun create(port: Int, onBeforeStartup: () -> Unit) =
      JvmDynamoDbServer.Factory.create(port) {
        onBeforeStartup()
        // Deterministically steal the reservation after release but before Jetty binds.
        if (++attempts <= collisions) blockers.add(ServerSocket(port))
      }

    fun close() = blockers.forEach { it.close() }
  }

  private fun close(service: TestDynamoDbService) {
    service.stopAsync()
    if (service.state() != com.google.common.util.concurrent.Service.State.FAILED) {
      service.awaitTerminated(10, SECONDS)
    }
    service.client.stopAsync().awaitTerminated(10, SECONDS)
    service.client.dynamoDb.close()
    service.client.asyncDynamoDb.close()
    service.client.dynamoDbStreams.close()
    service.client.asyncDynamoDbStreams.close()
  }
}
