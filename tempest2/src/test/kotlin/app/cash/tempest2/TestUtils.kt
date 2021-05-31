package app.cash.tempest2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

// Exists to silence Intellij warning "Junit test should return Unit".
fun runBlockingTest(block: suspend CoroutineScope.() -> Unit) {
  runBlocking(block = block)
}
