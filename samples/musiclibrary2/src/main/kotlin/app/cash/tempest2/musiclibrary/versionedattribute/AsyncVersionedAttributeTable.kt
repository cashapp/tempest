package app.cash.tempest2.musiclibrary.versionedattribute

import app.cash.tempest2.AsyncInlineView
import app.cash.tempest2.AsyncLogicalTable
import app.cash.tempest2.Attribute
import java.time.Instant

interface AsyncVersionedAttributeTable : AsyncLogicalTable<VersionedAttributeItem> {
  val attributes: AsyncInlineView<VersionedAttribute.Key, VersionedAttribute>
  val parallelAttributes: AsyncInlineView<ParallelVersionedAttribute.Key, ParallelVersionedAttribute>
}

/**
 * Stores the current version and time stamps as it writes
 * the next version to verify parallel write behavior.
 */
data class ParallelVersionedAttribute(
  val partition_key: String,
  val description: String,

  val created_at: Instant? = null,
  val updated_at: Instant? = null,

  val one_version: Long? = null,
  val one_created_at: Instant? = null,
  val one_updated_at: Instant? = null,

  val two_version: Long? = null,
  val two_created_at: Instant? = null,
  val two_updated_at: Instant? = null,

  val three_version: Long? = null,
  val three_created_at: Instant? = null,
  val three_updated_at: Instant? = null,

  val four_version: Long? = null,
  val four_created_at: Instant? = null,
  val four_updated_at: Instant? = null,

  val version: Long? = null,
) {
  @Attribute(prefix = "PARALLEL_INFO_")
  val sort_key: String = ""

  @Transient
  val key = Key(partition_key)

  data class Key(
    val partition_key: String
  ) {
    val sort_key: String = ""
  }
}
