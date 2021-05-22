package app.cash.tempest.versionedattribute

import app.cash.tempest.Attribute
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable

interface VersionedAttributeTable : LogicalTable<VersionedAttributeItem> {
  val attributes: InlineView<VersionedAttribute.Key, VersionedAttribute>
}

data class VersionedAttribute(
  val partition_key: String,
  val updated_at: Long? = null,
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
