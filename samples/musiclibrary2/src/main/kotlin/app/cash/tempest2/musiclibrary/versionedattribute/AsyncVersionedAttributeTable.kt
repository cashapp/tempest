package app.cash.tempest2.musiclibrary.versionedattribute

import app.cash.tempest2.AsyncInlineView
import app.cash.tempest2.AsyncLogicalTable

interface AsyncVersionedAttributeTable : AsyncLogicalTable<VersionedAttributeItem> {
  val attributes: AsyncInlineView<VersionedAttribute.Key, VersionedAttribute>
}
