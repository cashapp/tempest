package app.cash.tempest2.musiclibrary.versionedattribute

import app.cash.tempest2.AsyncLogicalDb
import app.cash.tempest2.LogicalDb
import app.cash.tempest2.TableName

interface AsyncVersionedAttributeDb : AsyncLogicalDb {
  @TableName("versioned_attributes")
  val versionedAttributes: AsyncVersionedAttributeTable
}
