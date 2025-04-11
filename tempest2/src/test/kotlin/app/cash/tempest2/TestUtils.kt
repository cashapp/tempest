package app.cash.tempest2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration

// Exists to silence Intellij warning "Junit test should return Unit".
fun runBlockingTest(block: suspend CoroutineScope.() -> Unit) {
  runBlocking(block = block)
}

class FakeClock : Clock() {
  var epochNow = 452_001_600_000

  fun add(duration: Duration) {
    epochNow += duration.inWholeMilliseconds
  }

  override fun instant(): Instant {
    return Instant.ofEpochMilli(epochNow)
  }

  override fun withZone(zone: ZoneId?): Clock =
    TODO("Not yet implemented")

  override fun getZone(): ZoneId =
    TODO("Not yet implemented")
}
