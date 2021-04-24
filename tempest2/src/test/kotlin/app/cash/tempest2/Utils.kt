package app.cash.tempest2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

fun runBlockingTest(testBody: suspend CoroutineScope.() -> Unit) {
  return runBlocking { testBody() }
}
