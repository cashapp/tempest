package app.cash.tempest2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Exists to silence Intellij warning "Junit test should return Unit".
fun runBlockingTest(block: suspend CoroutineScope.() -> Unit) {
  runBlocking(block = block)
}

/**
 * @param tickOnNow ticks the clock forward this amount every time [instant] is called, result includes the updated time.
 *
 * Test should use [getInstant] et al. to read the current time without ticking the clock.
 */
class FakeClock(val tickOnNow: Duration = 0.seconds) : Clock() {
  private var epochNow = AtomicLong(452_001_600_000)

  fun add(duration: Duration) {
    epochNow.addAndGet(duration.inWholeMilliseconds)
  }

  fun getInstant() = Instant.ofEpochMilli(epochNow.get())

  fun getDate() = Date.from(Instant.ofEpochMilli(epochNow.get()))

  fun minusTicks(ticks: Int) = Instant.ofEpochMilli(epochNow.addAndGet(-(ticks * tickOnNow.inWholeMilliseconds)))

  override fun instant(): Instant {
    return Instant.ofEpochMilli(epochNow.addAndGet(tickOnNow.inWholeMilliseconds))
  }

  override fun withZone(zone: ZoneId?): Clock =
    TODO("Not yet implemented")

  override fun getZone(): ZoneId =
    ZoneId.systemDefault()
}
