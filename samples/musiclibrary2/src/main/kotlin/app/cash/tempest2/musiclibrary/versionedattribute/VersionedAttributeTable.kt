package app.cash.tempest2.musiclibrary.versionedattribute

import app.cash.tempest2.Attribute
import app.cash.tempest2.InlineView
import app.cash.tempest2.LogicalTable
import java.time.Instant
import java.util.Date

interface VersionedAttributeTable : LogicalTable<VersionedAttributeItem> {
  val attributes: InlineView<VersionedAttribute.Key, VersionedAttribute>
}

data class VersionedAttribute(
  val partition_key: String,
  val description: String,

  val created_at_instant: Instant? = null,
  val created_at_date: Date? = null,

  val updated_at_instant: Instant? = null,
  val updated_at_date: Date? = null,

  val updated_at_dynamo: Instant? = null,

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

  /**
   * Override to include fractional seconds in Date printing.
   */
  override fun toString(): String =
    StringBuilder().apply {
      append("VersionedAttribute(")
      append("partition_key=$partition_key, ")
      append("description=$description, ")
      append("created_at_instant=$created_at_instant, ")
      append("created_at_date=${created_at_date?.toInstant()}, ")
      append("updated_at_instant=$updated_at_instant, ")
      append("updated_at_date=${updated_at_date?.toInstant()}, ")
      append("updated_at_dynamo=$updated_at_dynamo, ")
      append("version=$version")
      append(")")
    }.toString()
}
