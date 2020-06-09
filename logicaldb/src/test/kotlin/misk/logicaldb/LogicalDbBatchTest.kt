package misk.logicaldb

import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.logicaldb.example.AlbumTrack
import misk.logicaldb.example.AlbumTrackKey
import misk.logicaldb.example.MusicDb
import misk.logicaldb.example.MusicDbTestModule
import misk.logicaldb.example.PlaylistEntry
import misk.logicaldb.example.PlaylistEntryKey
import misk.logicaldb.example.PlaylistInfo
import misk.logicaldb.example.PlaylistInfoKey
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.inject.Inject

@MiskTest(startService = true)
class LogicalDbBatchTest {
  @MiskTestModule
  val module = MusicDbTestModule()
  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb

  @Test
  fun batchLoad() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"))
    assertThat(items).containsAll(albumTracks)
  }

  @Test
  fun batchLoadMultipleTables() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    for (albumTrack in albumTracks) {
      musicDb.albums.tracks.save(albumTrack)
    }
    val playlistInfo = PlaylistInfo("L_1", "WFH Music", 1)
    musicDb.playlists.info.save(playlistInfo)
    val playlistEntry = PlaylistEntry("L_1", "M_1:T_1")
    musicDb.playlists.entries.save(playlistEntry)
    val playlistEntry2 = PlaylistEntry("L_1", "M_3:T_2")
    musicDb.playlists.entries.save(playlistEntry2)

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"),
        PlaylistInfoKey("L_1"),
        PlaylistEntryKey("L_1", "M_1:T_1"),
        PlaylistEntryKey("L_1", "M_3:T_2"))
    assertThat(items.getItems<AlbumTrack>()).containsAll(albumTracks)
    assertThat(items.getItems<PlaylistInfo>()).contains(playlistInfo)
    assertThat(items.getItems<PlaylistEntry>()).contains(playlistEntry, playlistEntry2)
  }

  @Test
  fun batchLoadAfterBatchWrite() {
    val albumTracks = listOf(
        AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S")),
        AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S")),
        AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    )
    musicDb.batchWrite(BatchWriteSet.Builder().clobber(albumTracks).build())

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"))
    assertThat(items).containsAll(albumTracks)
  }

  @Test
  fun batchLoadAfterBatchDelete() {
    val t1 = AlbumTrack("M_1", "T_1", "Dreamin'", Duration.parse("PT3M28S"))
    val t2 = AlbumTrack("M_1", "T_2", "what you do to me", Duration.parse("PT3M24S"))
    val t3 = AlbumTrack("M_1", "T_3", "too slow", Duration.parse("PT2M27S"))
    musicDb.batchWrite(BatchWriteSet.Builder().clobber(t1, t2, t3).build())

    musicDb.batchWrite(BatchWriteSet.Builder().delete(AlbumTrackKey("M_1", "T_2")).build())

    val items = musicDb.batchLoad(
        AlbumTrackKey("M_1", "T_1"),
        AlbumTrackKey("M_1", "T_2"),
        AlbumTrackKey("M_1", "T_3"))
    assertThat(items).contains(t1, t3)
  }
}
