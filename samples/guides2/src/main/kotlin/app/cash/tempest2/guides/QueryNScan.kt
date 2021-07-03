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

package app.cash.tempest2.guides

import app.cash.tempest2.BeginsWith
import app.cash.tempest2.Between
import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import app.cash.tempest2.musiclibrary.AlbumInfoOrTrack
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Duration

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
        endInclusive = AlbumTrack.Key(albumToken, track_number = 9)
      )
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

  private fun runLengthLongerThan(duration: Duration): Expression {
    return Expression.builder()
      .expression("run_length > :duration")
      .expressionValues(
        mapOf(
          ":duration" to AttributeValue.builder().s(duration.toString()).build()
        )
      )
      .build()
  }

  // Query - Pagination.
  fun loadAlbumTracks6(albumToken: String): List<AlbumTrack> {
    val tracks = mutableListOf<AlbumTrack>()
    lateinit var page: Page<AlbumTrack.Key, AlbumTrack>
    do {
      page = table.albumTracks.query(
        keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
        pageSize = 10,
        initialOffset = page.offset
      )
      tracks.addAll(page.contents)
    } while (page.hasMorePages)
    return tracks.toList()
  }

  // Query - Specified Offset
  fun loadAlbumTracksAfterTrack(albumToken: String, trackToken: String): List<AlbumTrack> {
    val tracks = mutableListOf<AlbumTrack>()
    lateinit var page: Page<AlbumTrack.Key, AlbumTrack>
    val offset = Offset(AlbumTrack.Key(trackToken))
    do {
      page = table.albumTracks.query(
        keyCondition = BeginsWith(AlbumTrack.Key(albumToken)),
        pageSize = 10,
        initialOffset = page.offset ?: offset
      )
      tracks.addAll(page.contents)
    } while (page.hasMorePages)
    return tracks.toList()
  }

  // Query - Mixed types
  fun loadAlbumInfoAndTracks(albumToken: String): List<AlbumInfoOrTrack> {
    val page = table.albumInfoOrTracks.query(
      keyCondition = BeginsWith(
        prefix = AlbumInfoOrTrack.Key(albumToken)
      )
    )
    return page.contents
  }

  // Scan.
  fun loadAllAlbumTracks(): List<AlbumTrack> {
    val page = table.albumTracks.scan()
    return page.contents
  }

  // Scan - Parallel.
  // Not supported.
}
