package app.cash.tempest2.testing.internal

import app.cash.tempest2.testing.TestDynamoDbClient
import app.cash.tempest2.testing.TestDynamoDbServer
import app.cash.tempest2.testing.TestTable
import com.google.common.util.concurrent.AbstractIdleService
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

/**
 * This Guava service spins up a DynamoDB server in tests. It shares the server across tests and
 * keeps the server running until the process exits.
 */
class TestDynamoDbService(
  val client: TestDynamoDbClient,
  val server: TestDynamoDbServer
) : AbstractIdleService() {

  // Tests set this before starting the service to control the claim/start interleaving.
  internal var onStartupDecisionForTest: (Boolean) -> Unit = {}

  override fun startUp() {
    server.startIfNeeded()
    log.info { "starting client for ${server.id}" }
    client.startAsync()
    client.awaitRunning()
    log.info { "client started for ${server.id}" }
  }

  override fun shutDown() {
    client.stopAsync()
    client.awaitTerminated()
  }

  private fun TestDynamoDbServer.startIfNeeded() {
    val startup = CompletableFuture<Unit>()
    val existingStartup = serverStarts.putIfAbsent(id, startup)
    val ownsStartup = existingStartup == null
    try {
      onStartupDecisionForTest(ownsStartup)
      if (existingStartup != null) {
        try {
          existingStartup.join()
        } catch (e: CompletionException) {
          throw (e.cause ?: e)
        }
        log.info { "$id already running, not starting anything" }
        return
      }
      val startTime = System.currentTimeMillis()
      log.info { "starting $id" }
      startAsync()
      awaitRunning()
      val elapsed = System.currentTimeMillis() - startTime
      log.info { "$id started in ${elapsed}ms" }
      Runtime.getRuntime().addShutdownHook(
        Thread {
          log.info { "stopping $id" }
          stopAsync()
          awaitTerminated()
        }
      )
      startup.complete(Unit)
    } catch (failure: Throwable) {
      if (ownsStartup) {
        startup.completeExceptionally(failure)
        serverStarts.remove(id, startup)
      }
      throw failure
    }
  }

  companion object {
    private val allocatedSockets = ConcurrentHashMap<String, ServerSocket>()
    private fun defaultPortHolder(key: String): PortHolder {
      // Only pick random port once to share one test server with multiple tests.
      val socket = allocatedSockets.getOrPut(key) { allocateRandomPort() }
      return PortHolder(socket.localPort) {
        if (!socket.isClosed) {
          val portNumber = socket.localPort
          socket.close()
          log.info { "released port $portNumber" }
        }
      }
    }

    private val serverStarts = ConcurrentHashMap<String, CompletableFuture<Unit>>()
    private val log = getLogger<TestDynamoDbService>()

    @JvmStatic
    fun create(
      serverFactory: TestDynamoDbServer.Factory<*>,
      tables: List<TestTable>,
      port: Int? = null
    ): TestDynamoDbService {
      val portHolder = port?.let { PortHolder(it) } ?: defaultPortHolder(serverFactory.toString())
      return TestDynamoDbService(
        DefaultTestDynamoDbClient(tables, serverFactory.hostName(portHolder.value), portHolder.value),
        serverFactory.create(portHolder.value, portHolder.releasePort)
      )
    }
  }

  private data class PortHolder(val value: Int, val releasePort: () -> Unit = {})
}
