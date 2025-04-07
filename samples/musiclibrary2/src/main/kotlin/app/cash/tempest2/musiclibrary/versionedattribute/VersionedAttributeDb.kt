package app.cash.tempest2.musiclibrary.versionedattribute

import app.cash.tempest2.LogicalDb
import app.cash.tempest2.TableName

interface VersionedAttributeDb : LogicalDb {
  @TableName("versioned_attributes")
  val versionedAttributes: VersionedAttributeTable
}
