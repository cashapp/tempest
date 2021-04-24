package app.cash.tempest2.musiclibrary.async

import app.cash.tempest2.TableName
import app.cash.tempest2.async.LogicalDb

interface MusicDb : LogicalDb {
  @TableName("music_items")
  val music: MusicTable
}
