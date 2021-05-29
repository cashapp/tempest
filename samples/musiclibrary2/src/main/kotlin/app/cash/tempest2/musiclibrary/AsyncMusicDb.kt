package app.cash.tempest2.musiclibrary

import app.cash.tempest2.AsyncLogicalDb
import app.cash.tempest2.TableName

interface AsyncMusicDb : AsyncLogicalDb {
  @TableName("music_items")
  val music: AsyncMusicTable
}
