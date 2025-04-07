package app.cash.tempest2.musiclibrary.versionedattribute

import app.cash.tempest2.Attribute
import app.cash.tempest2.InlineView
import app.cash.tempest2.LogicalTable
import java.time.Instant

interface VersionedAttributeTable : LogicalTable<VersionedAttributeItem> {
  val attributes: InlineView<VersionedAttribute.Key, VersionedAttribute>
}

data class VersionedAttribute(
  val partition_key: String,
  val description: String,
  val created_at: Instant,
  val updated_at: Instant? = null,
  val version: Long? = null,
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""

  @Transient
  val key = Key(partition_key)

  data class Key(
    val partition_key: String
  ) {
    val sort_key: String = ""
  }
}
