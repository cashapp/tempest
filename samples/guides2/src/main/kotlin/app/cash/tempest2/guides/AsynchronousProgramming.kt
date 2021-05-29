package app.cash.tempest2.guides

import app.cash.tempest2.musiclibrary.AsyncMusicTable
import app.cash.tempest2.musiclibrary.PlaylistInfo
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class AsynchronousProgramming(
  private val table: AsyncMusicTable,
) {

  suspend fun changePlaylistName(playlistToken: String, newName: String) {
    // Read.
    val existing = checkNotNull(
      table.playlistInfo.load(PlaylistInfo.Key(playlistToken)) // This is a suspend function.
    ) { "Playlist does not exist: $playlistToken" }
    // Modify.
    val newPlaylist = existing.copy(
      playlist_name = newName,
      playlist_version = existing.playlist_version + 1
    )
    // Write.
    table.playlistInfo.save( // This is a suspend function.
      newPlaylist,
      ifPlaylistVersionIs(existing.playlist_version)
    )
  }

  private fun ifPlaylistVersionIs(playlist_version: Long): Expression {
    return Expression.builder()
      .expression("playlist_version = :playlist_version")
      .expressionValues(mapOf(":playlist_version" to AttributeValue.builder().n("$playlist_version").build()))
      .build()
  }
}