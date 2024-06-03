package app.cash.tempest.testing.internal

import app.cash.tempest.testing.TestDynamoDbClient
import app.cash.tempest.testing.TestDynamoDbServer
import app.cash.tempest.testing.TestTable
import com.google.common.util.concurrent.AbstractIdleService
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * This Guava service spins up a DynamoDB server in tests. It shares the server across tests and
 * keeps the server running until the process exits.
 */
class TestDynamoDbService private constructor(
  val client: TestDynamoDbClient,
  val server: TestDynamoDbServer
) : AbstractIdleService() {

  override fun startUp() {
    server.startIfNeeded()
    client.startAsync()
    client.awaitRunning()
  }

  override fun shutDown() {
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

  companion object {
    private val allocatedSockets = ConcurrentHashMap<String, ServerSocket>()
    private fun defaultPortHolder(key: String): PortHolder {
      // Only pick random port once to share one test server with multiple tests.
      val socket = allocatedSockets.getOrPut(key) { allocateRandomPort() }
      return PortHolder(socket.localPort) {
        if (!socket.isClosed) {
          socket.close()
        }
      }
    }

    private val runningServers = ConcurrentHashMap.newKeySet<String>()
    private val log = getLogger<TestDynamoDbService>()

    @JvmStatic
    fun create(
      serverFactory: TestDynamoDbServer.Factory<*>,
      tables: List<TestTable>,
      port: Int? = null
    ): TestDynamoDbService {
      val portHolder = port?.let { PortHolder(it) } ?: defaultPortHolder(serverFactory.toString())
      return TestDynamoDbService(
        DefaultTestDynamoDbClient(tables, portHolder.value),
        serverFactory.create(portHolder.value, portHolder.releasePort)
      )
    }
  }

  private data class PortHolder(val value: Int, val releasePort: () -> Unit = {})
}
