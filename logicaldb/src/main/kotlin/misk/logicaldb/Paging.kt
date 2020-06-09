package misk.logicaldb

data class Page<K, T>(
  val contents: List<T>,
  val offset: Offset<K>?
) {
  val hasMorePages: Boolean
    get() = offset != null
}

data class Offset<K>(
  val key: K
)
