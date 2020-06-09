package app.cash.tempest

interface Scannable<K : Any, I : Any> {
  fun scan(): Page<K, I> = TODO("")
  fun parallelScan(threads: Int): Page<K, I> = TODO("")
  fun count(): Int = TODO("")
}
