/*
 * Copyright 2020 Square Inc.
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

import app.cash.tempest.BeginsWith
import app.cash.tempest.Between
import app.cash.tempest.FilterExpression
import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.WorkerId
import app.cash.tempest.musiclibrary.AlbumTrack
import app.cash.tempest.musiclibrary.MusicTable
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class QueryNScan(
  private val table: MusicTable
) {

  // Query - Key Condition - Partition Key and Entity Type.
  fun loadAlbumTracks(albumToken: String): List<AlbumTrack> {
    val page = table.albumTracks.query(
      keyCondition = BeginsWith(
        prefix = AlbumTrack.Key(albumToken)
      )
    )
    return page.contents
  }

  // Query - Key Condition - Partition Key and Sort Key Prefix.
  fun loadAlbumTracks2(albumToken: String): List<AlbumTrack> {
    val page = table.albumTracksByTitle.query(
      keyCondition = BeginsWith(
        prefix = AlbumTrack.TitleIndexOffset(albumToken, track_title = "I want ")
      )
    )
    return page.contents
  }

  // Query - Key Condition - Partition Key and Sort Key Range.
  fun loadAlbumTracks3(albumToken: String): List<AlbumTrack> {
    val page = table.albumTracks.query(
      keyCondition = Between(
        startInclusive = AlbumTrack.Key(albumToken, track_number = 5),
        endInclusive = AlbumTrack.Key(albumToken, track_number = 9))
    )
    return page.contents
  }

  // Query - Descending Order.
  fun loadAlbumTracks4(albumToken: String): List<AlbumTrack> {
    val page = table.albumTracks.query(
      keyCondition = BeginsWith(
        prefix = AlbumTrack.Key(albumToken)
      ),
      asc = false
    )
    return page.contents
  }

  // Query - Filter Expression
  fun loadAlbumTracks5(albumToken: String): List<AlbumTrack> {
    val page = table.albumTracks.query(
      keyCondition = BeginsWith(prefix = AlbumTrack.Key(albumToken)),
      filterExpression = runLengthLongerThan(Duration.ofMinutes(3))
    )
    return page.contents
  }

  private fun runLengthLongerThan(duration: Duration): FilterExpression {
    return FilterExpression(
      "run_length > :duration",
      mapOf(
        ":duration" to AttributeValue().withS(duration.toString())
      )
    )
  }

  // Query - Pagination.
  fun loadAlbumTracks6(albumToken: String): List<AlbumTrack> {
    val tracks = mutableListOf<AlbumTrack>()
    var page: Page<AlbumTrack.Key, AlbumTrack>? = null
    do {
      page = table.albumTracks.query(
        keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
        pageSize = 10,
        initialOffset = page?.offset
      )
      tracks.addAll(page.contents)
    } while (page?.hasMorePages == true)
    return tracks.toList()
  }

  // Query - Specified Offset
  fun loadAlbumTracksAfterTrack(albumToken: String, trackToken: String): List<AlbumTrack> {
    val tracks = mutableListOf<AlbumTrack>()
    var page: Page<AlbumTrack.Key, AlbumTrack>? = null
    val offset = Offset(AlbumTrack.Key(trackToken))
    do {
      page = table.albumTracks.query(
        keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
        pageSize = 10,
        initialOffset = page?.offset ?: offset
      )
      tracks.addAll(page.contents)
    } while (page?.hasMorePages == true)
    return tracks.toList()
  }

  // Scan.
  fun loadAllAlbumTracks(): List<AlbumTrack> {
    val page = table.albumTracks.scan()
    return page.contents
  }

  // Scan - Parallel.
  fun loadAllAlbumTracks2(): List<AlbumTrack> = runBlocking {
    val segment1 = async { loadSegment(1) }
    val segment2 = async { loadSegment(2) }
    segment1.await() + segment2.await()
  }

  private fun loadSegment(segment: Int): List<AlbumTrack> {
    val page = table.albumTracks.scan(
      workerId = WorkerId(segment, totalSegments = 2)
    )
    return page.contents
  }
}
