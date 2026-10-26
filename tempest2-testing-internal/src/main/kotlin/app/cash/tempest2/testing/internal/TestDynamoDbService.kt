package app.cash.tempest2.testing.internal

import app.cash.tempest2.testing.TestDynamoDbClient
import app.cash.tempest2.testing.TestDynamoDbServer
import app.cash.tempest2.testing.TestTable
import com.google.common.util.concurrent.AbstractIdleService
import java.net.BindException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * This Guava service spins up a DynamoDB server in tests. It shares the server across tests and
 * keeps the server running until the process exits.
 */
class TestDynamoDbService(
  val client: TestDynamoDbClient,
  val server: TestDynamoDbServer
) : AbstractIdleService() {

  override fun startUp() {
    synchronized(server) { server.startIfNeeded() }
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
    if (runningServers.contains(id)) {
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
    runningServers.add(id)
  }

  companion object {
    private fun reservePort(): PortHolder {
      val socket = allocateRandomPort()
      return PortHolder(socket.localPort) {
        if (!socket.isClosed) {
          val portNumber = socket.localPort
          socket.close()
          log.info { "released port $portNumber" }
        }
      }
    }

    private val sharedServers = ConcurrentHashMap<String, TestDynamoDbServer>()

    private val runningServers = ConcurrentHashMap.newKeySet<String>()
    private val log = getLogger<TestDynamoDbService>()

    @JvmStatic
    fun create(
      serverFactory: TestDynamoDbServer.Factory<*>,
      tables: List<TestTable>,
      port: Int? = null
    ): TestDynamoDbService {
      val server = if (port != null) {
        serverFactory.create(port)
      } else {
        // Share both the selected port and retry state across injectors in this JVM.
        sharedServers.computeIfAbsent(serverFactory.toString()) {
          RetryingServer(serverFactory, reservePort())
        }
      }
      return TestDynamoDbService(
        DefaultTestDynamoDbClient(tables) {
          URI.create("http://${serverFactory.hostName(server.port)}:${server.port}")
        },
        server
      )
    }
  }

  /** Only automatically allocated ports may move; callers requesting a port retain fail-fast behavior. */
  private class RetryingServer(
    private val factory: TestDynamoDbServer.Factory<*>,
    initialPort: PortHolder
  ) : AbstractIdleService(), TestDynamoDbServer {
    override val id = "tempest-retrying-${factory}-${initialPort.value}"
    @Volatile
    private var delegate = factory.create(initialPort.value, initialPort.releasePort)
    override val port: Int get() = delegate.port

    override fun startUp() {
      val failures = mutableListOf<Exception>()
      for (attempt in 1..3) {
        try {
          delegate.startAsync()
          delegate.awaitRunning()
          return
        } catch (e: Exception) {
          val bindFailure = generateSequence<Throwable>(e) { it.cause }.any { it is BindException }
          if (!bindFailure || attempt == 3) {
            failures.forEach { e.addSuppressed(it) }
            throw e
          }
          failures.add(e)
          log.warn(e) { "DynamoDB bind failed on port $port (attempt $attempt/3); allocating a fresh port" }
          val socket = allocateRandomPort()
          delegate = factory.create(socket.localPort) { socket.close() }
        }
      }
    }

    override fun shutDown() {
      delegate.stopAsync()
      delegate.awaitTerminated()
    }
  }

  private data class PortHolder(val value: Int, val releasePort: () -> Unit = {})
}
