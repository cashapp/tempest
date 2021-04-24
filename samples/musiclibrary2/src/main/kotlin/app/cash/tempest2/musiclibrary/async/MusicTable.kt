package app.cash.tempest2.musiclibrary.async

import app.cash.tempest2.async.InlineView
import app.cash.tempest2.async.LogicalTable
import app.cash.tempest2.async.SecondaryIndex
import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicItem
import app.cash.tempest2.musiclibrary.PlaylistInfo

interface MusicTable : LogicalTable<MusicItem> {
  val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>

  val playlistInfo: InlineView<PlaylistInfo.Key, PlaylistInfo>

  // Global Secondary Indexes.
  val albumInfoByGenre: SecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo>
  val albumInfoByArtist: SecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo>

  // Local Secondary Indexes.
  val albumTracksByTitle: SecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack>
}
