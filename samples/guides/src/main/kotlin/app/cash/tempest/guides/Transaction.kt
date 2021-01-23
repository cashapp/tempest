/*
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.tempest.guides

import app.cash.tempest.TransactionWriteSet
import app.cash.tempest.WritingPager
import app.cash.tempest.musiclibrary.AlbumTrack
import app.cash.tempest.musiclibrary.MusicDb
import app.cash.tempest.musiclibrary.MusicTable
import app.cash.tempest.musiclibrary.PlaylistInfo
import app.cash.tempest.transactionWritingPager
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue

class Transaction(
  private val db: MusicDb
) {
  private val table: MusicTable = db.music

  // Transactional Read.
  fun loadPlaylistTracks(playlist: PlaylistInfo): List<AlbumTrack> {
    val results = db.transactionLoad(
      playlist.playlist_tracks // [ AlbumTrack.Key("ALBUM_1", track_number = 1), AlbumTrack.Key("ALBUM_354", 12), ... ]
    )
    return results.getItems<AlbumTrack>()
  }

  // Transactional Write.
  fun addTrackToPlaylist(
    playlistToken: String,
    albumTrack: AlbumTrack.Key
  ) {
    // Read.
    val existing = checkNotNull(
      table.playlistInfo.load(PlaylistInfo.Key(playlistToken))
    ) { "Playlist does not exist: $playlistToken" }
    // Modify.
    val newPlaylist = existing.copy(
      playlist_tracks = existing.playlist_tracks + albumTrack,
      playlist_version = existing.playlist_version + 1
    )
    // Write.
    val writeSet = TransactionWriteSet.Builder()
      .save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
      // Add a playlist entry only if the album track exists.
      .checkCondition(albumTrack, trackExists())
      .build()
    db.transactionWrite(writeSet)
  }

  // Transactional Write - Writing Pager.
  fun addTracksToPlaylist(
    playlistToken: String,
    albumTracks: List<AlbumTrack.Key>
  ) {
    db.transactionWritingPager(
      albumTracks,
      maxTransactionItems = 25,
      handler = AlbumTrackWritingPagerHandler(playlistToken, table)
    ).execute()
  }
}

private fun ifPlaylistVersionIs(playlist_version: Long): DynamoDBTransactionWriteExpression {
  return DynamoDBTransactionWriteExpression()
    .withConditionExpression("playlist_version = :playlist_version")
    .withExpressionAttributeValues(
      mapOf(
        ":playlist_version" to AttributeValue().withN("$playlist_version")
      )
    )
}

private fun trackExists(): DynamoDBTransactionWriteExpression {
  return DynamoDBTransactionWriteExpression()
    .withConditionExpression("attribute_exists(track_title)")
}

class AlbumTrackWritingPagerHandler(
  private val playlistToken: String,
  private val table: MusicTable
) : WritingPager.Handler<AlbumTrack.Key> {
  private lateinit var currentPagePlaylistInfo: PlaylistInfo
  private lateinit var currentPageTracks: List<AlbumTrack.Key>

  override fun eachPage(proceed: () -> Unit) {
    proceed()
  }

  override fun beforePage(
    remainingUpdates: List<AlbumTrack.Key>,
    maxTransactionItems: Int
  ): Int {
    // Reserve 1 for the playlist info at the end.
    currentPageTracks = remainingUpdates.take((maxTransactionItems - 1))
    currentPagePlaylistInfo = table.playlistInfo.load(PlaylistInfo.Key(playlistToken))!!
    return currentPageTracks.size
  }

  override fun item(builder: TransactionWriteSet.Builder, item: AlbumTrack.Key) {
    builder.checkCondition(item, trackExists())
  }

  override fun finishPage(builder: TransactionWriteSet.Builder) {
    val existing = currentPagePlaylistInfo
    val newPlaylist = existing.copy(
      playlist_tracks = existing.playlist_tracks + currentPageTracks,
      playlist_version = existing.playlist_version + 1
    )
    builder.save(newPlaylist, ifPlaylistVersionIs(existing.playlist_version))
  }
}
