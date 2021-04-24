package app.cash.tempest2.musiclibrary

import app.cash.tempest2.AsyncInlineView
import app.cash.tempest2.AsyncLogicalTable
import app.cash.tempest2.AsyncSecondaryIndex

interface AsyncMusicTable : AsyncLogicalTable<MusicItem> {
  val albumInfo: AsyncInlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: AsyncInlineView<AlbumTrack.Key, AlbumTrack>

  val playlistInfo: AsyncInlineView<PlaylistInfo.Key, PlaylistInfo>

  // Global Secondary Indexes.
  val albumInfoByGenre: AsyncSecondaryIndex<AlbumInfo.GenreIndexOffset, AlbumInfo>
  val albumInfoByArtist: AsyncSecondaryIndex<AlbumInfo.ArtistIndexOffset, AlbumInfo>

  // Local Secondary Indexes.
  val albumTracksByTitle: AsyncSecondaryIndex<AlbumTrack.TitleIndexOffset, AlbumTrack>
}
