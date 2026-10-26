package app.cash.tempest2.testing.internal

import app.cash.tempest2.testing.TestDynamoDbClient
import app.cash.tempest2.testing.TestDynamoDbServer
import app.cash.tempest2.testing.TestTable
import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

class TestDynamoDbServiceTest {
  @Test
  fun `same id has one starter and clients wait for successful startup`() {
    val attempt = ConcurrentAttempt()
    try {
      attempt.startAtDecisionBarrier()
      attempt.assertOneOwner()
      attempt.releaseDecisions()

      val winner = attempt.startedServers.takeEvent()
      attempt.assertClientsNotStarted()
      assertEquals(Service.State.STARTING, attempt.first.state())
      assertEquals(Service.State.STARTING, attempt.second.state())

      winner.succeed()
      attempt.first.awaitRunning(WAIT_SECONDS, SECONDS)
      attempt.second.awaitRunning(WAIT_SECONDS, SECONDS)

      assertTrue(attempt.startedServers.isEmpty(), "Only the claimed server may start")
      assertEquals(1, attempt.firstClient.startCount.get())
      assertEquals(1, attempt.secondClient.startCount.get())
      val events = attempt.events.toList()
      val ready = events.indexOf("ready:${winner.label}")
      assertTrue(ready >= 0)
      assertTrue(events.indexOf("client:first") > ready)
      assertTrue(events.indexOf("client:second") > ready)

      // A wrapper owns its client, while the shared server has process lifetime.
      attempt.first.stopAsync().awaitTerminated(WAIT_SECONDS, SECONDS)
      attempt.second.stopAsync().awaitTerminated(WAIT_SECONDS, SECONDS)
      assertEquals(Service.State.RUNNING, winner.state())
    } finally {
      attempt.close()
    }
  }

  @Test
  fun `startup failure reaches both callers and a later attempt can claim the id`() {
    val attempt = ConcurrentAttempt()
    val replacementServer =
      ControlledServer(attempt.id, "replacement", attempt.startedServers, attempt.events)
    val replacementClient = InertClient("replacement", attempt.events)
    // Exercise the unchanged public constructor with its production no-op observer.
    val replacement = TestDynamoDbService(replacementClient, replacementServer)
    try {
      attempt.startAtDecisionBarrier()
      attempt.assertOneOwner()
      attempt.releaseDecisions()

      val winner = attempt.startedServers.takeEvent()
      attempt.assertClientsNotStarted()
      val failure = IllegalStateException("controlled startup failure")
      winner.fail(failure)

      assertThrows(IllegalStateException::class.java) {
        attempt.first.awaitRunning(WAIT_SECONDS, SECONDS)
      }
      assertThrows(IllegalStateException::class.java) {
        attempt.second.awaitRunning(WAIT_SECONDS, SECONDS)
      }
      assertEquals(Service.State.FAILED, attempt.first.state())
      assertEquals(Service.State.FAILED, attempt.second.state())
      assertSame(attempt.first.failureCause(), attempt.second.failureCause())
      assertSame(failure, attempt.first.failureCause().cause)
      assertTrue(attempt.startedServers.isEmpty(), "A follower must not start its own server")
      attempt.assertClientsNotStarted()

      // Both failures are visible only after the winning failure path removed its claim.
      replacement.startAsync()
      assertSame(replacementServer, attempt.startedServers.takeEvent())
      replacementServer.succeed()
      replacement.awaitRunning(WAIT_SECONDS, SECONDS)
      assertEquals(1, replacementClient.startCount.get())
    } finally {
      replacementServer.succeed()
      attempt.close(replacement, replacementClient, replacementServer)
    }
  }

  private class ConcurrentAttempt {
    val id = "startup-registry-test-${nextId.incrementAndGet()}"
    val events = ConcurrentLinkedQueue<String>()
    val startedServers = LinkedBlockingQueue<ControlledServer>()
    private val decisions = LinkedBlockingQueue<Boolean>()
    private val proceed = CountDownLatch(1)
    private val firstServer = ControlledServer(id, "first", startedServers, events)
    private val secondServer = ControlledServer(id, "second", startedServers, events)
    val firstClient = InertClient("first", events)
    val secondClient = InertClient("second", events)
    val first = service(firstClient, firstServer)
    val second = service(secondClient, secondServer)
    private var observedDecisions = emptyList<Boolean>()

    private fun service(client: InertClient, server: ControlledServer) =
      TestDynamoDbService(client, server).apply {
        onStartupDecisionForTest = { ownsStartup ->
          decisions.add(ownsStartup)
          check(proceed.await(WAIT_SECONDS, SECONDS)) { "Startup decision gate was not released" }
        }
      }

    fun startAtDecisionBarrier() {
      first.startAsync()
      second.startAsync()
      observedDecisions = listOf(decisions.takeEvent(), decisions.takeEvent())
      // Both actual decisions have completed while neither server has started.
      assertTrue(startedServers.isEmpty())
      assertClientsNotStarted()
    }

    fun assertOneOwner() {
      assertEquals(1, observedDecisions.count { it }, "Only one caller may own startup")
      assertEquals(1, observedDecisions.count { !it }, "The other caller must follow the winner")
    }

    fun releaseDecisions() = proceed.countDown()

    fun assertClientsNotStarted() {
      assertEquals(0, firstClient.startCount.get())
      assertEquals(0, secondClient.startCount.get())
    }

    fun close(vararg additionalServices: Service) {
      // Complete even a server that has not reached doStart yet, so assertion failures cannot strand a worker.
      proceed.countDown()
      firstServer.succeed()
      secondServer.succeed()
      stopForCleanup(
        first,
        second,
        firstClient,
        secondClient,
        firstServer,
        secondServer,
        *additionalServices
      )
    }
  }

  private class ControlledServer(
    override val id: String,
    val label: String,
    private val starts: BlockingQueue<ControlledServer>,
    private val events: ConcurrentLinkedQueue<String>
  ) : AbstractService(), TestDynamoDbServer {
    // An interface value only; this fake never allocates or binds a socket.
    override val port = 0
    private val outcome = CompletableFuture<Unit>()

    override fun doStart() {
      starts.add(this)
      outcome.whenComplete { _, failure ->
        if (failure == null) {
          events.add("ready:$label")
          notifyStarted()
        } else {
          notifyFailed(failure)
        }
      }
    }

    override fun doStop() = notifyStopped()

    fun succeed() {
      outcome.complete(Unit)
    }

    fun fail(failure: Throwable) {
      outcome.completeExceptionally(failure)
    }
  }

  private class InertClient(
    private val label: String,
    private val events: ConcurrentLinkedQueue<String>
  ) : AbstractService(), TestDynamoDbClient {
    val startCount = AtomicInteger()
    override val tables = emptyList<TestTable>()
    override val dynamoDb: DynamoDbClient get() = error("SDK client access is not part of this test")
    override val asyncDynamoDb: DynamoDbAsyncClient get() = error("SDK client access is not part of this test")
    override val dynamoDbStreams: DynamoDbStreamsClient get() = error("SDK client access is not part of this test")
    override val asyncDynamoDbStreams: DynamoDbStreamsAsyncClient
      get() = error("SDK client access is not part of this test")

    override fun doStart() {
      startCount.incrementAndGet()
      events.add("client:$label")
      notifyStarted()
    }

    override fun doStop() = notifyStopped()

    override fun reset() = Unit
  }

  companion object {
    // These are deadlock bounds, not the correctness oracle or a simulated clock.
    private const val WAIT_SECONDS = 10L
    private val nextId = AtomicInteger()

    private fun <T> BlockingQueue<T>.takeEvent(): T =
      poll(WAIT_SECONDS, SECONDS) ?: throw AssertionError("Missing controlled lifecycle event")

    private fun stopForCleanup(vararg services: Service) {
      var cleanupFailure: Throwable? = null
      for (service in services) {
        try {
          if (service.state() != Service.State.FAILED) {
            service.stopAsync().awaitTerminated(WAIT_SECONDS, SECONDS)
          }
        } catch (failure: Throwable) {
          val firstFailure = cleanupFailure
          if (firstFailure == null) {
            cleanupFailure = failure
          } else {
            firstFailure.addSuppressed(failure)
          }
        }
      }
      cleanupFailure?.let { throw it }
    }
  }
}
