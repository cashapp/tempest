package app.cash.tempest.versionedattribute

import app.cash.tempest.LogicalDb

interface VersionedAttributeDb : LogicalDb {
  val versionedAttributes: VersionedAttributeTable
}
