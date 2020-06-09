package app.cash.tempest

interface Queryable<K : Any, I : Any> {
  /** Reads up to the [pageSize] items or a maximum of 1 MB of data. */
  fun query(
    startInclusive: K,
    endExclusive: K,
    consistentRead: Boolean = false,
    asc: Boolean = true,
    pageSize: Int = 100,
    initialOffset: Offset<K>? = null
  ): Page<K, I>
}
