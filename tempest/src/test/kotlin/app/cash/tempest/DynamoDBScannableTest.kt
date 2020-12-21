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

package app.cash.tempest

import app.cash.tempest.musiclibrary.AFTER_HOURS_EP
import app.cash.tempest.musiclibrary.LOCKDOWN_SINGLE
import app.cash.tempest.musiclibrary.MusicDb
import app.cash.tempest.musiclibrary.MusicDbTestModule
import app.cash.tempest.musiclibrary.THE_DARK_SIDE_OF_THE_MOON
import app.cash.tempest.musiclibrary.THE_WALL
import app.cash.tempest.musiclibrary.WHAT_YOU_DO_TO_ME_SINGLE
import app.cash.tempest.musiclibrary.albumTitles
import app.cash.tempest.musiclibrary.givenAlbums
import app.cash.tempest.musiclibrary.trackTitles
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.time.Duration
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDBScannableTest {

  @MiskTestModule
  val module = MusicDbTestModule()

  @Inject lateinit var musicDb: MusicDb

  private val musicTable get() = musicDb.music

  @Test
  fun primaryIndex() {
    musicTable.givenAlbums(
      THE_DARK_SIDE_OF_THE_MOON,
      THE_WALL,
      WHAT_YOU_DO_TO_ME_SINGLE,
      AFTER_HOURS_EP,
      LOCKDOWN_SINGLE
    )

    val page1 = musicTable.albumInfo.scan(
      filterExpression = releaseYearIs(2020)
    )

    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.albumTitles).containsExactly(
      AFTER_HOURS_EP.album_title,
      LOCKDOWN_SINGLE.album_title
    )
  }

  @Test
  fun primaryIndexParallelScan() {
    musicTable.givenAlbums(THE_WALL)

    val worker1Page1 = musicTable.albumTracks.scan(
      workerId = WorkerId(0, 2),
      pageSize = 50,
      filterExpression = isTrack()
    )
    assertThat(worker1Page1.hasMorePages).isFalse()

    val worker2Page1 = musicTable.albumTracks.scan(
      workerId = WorkerId(1, 2),
      pageSize = 50,
      filterExpression = isTrack()
    )
    assertThat(worker1Page1.hasMorePages).isFalse()

    assertThat(worker1Page1.trackTitles + worker2Page1.trackTitles)
      .containsExactlyInAnyOrderElementsOf(THE_WALL.trackTitles)
  }

  @Test
  fun localSecondaryIndex() {
    musicTable.givenAlbums(THE_WALL)
    val expectedTrackTitles = THE_WALL.trackTitles.sorted()

    val page1 = musicTable.albumTracksByTitle.scan(
      pageSize = 20
    )
    assertThat(page1.hasMorePages).isTrue()
    assertThat(page1.trackTitles).containsAll(expectedTrackTitles.slice(0..19))

    val page2 = musicTable.albumTracksByTitle.scan(
      pageSize = 20,
      initialOffset = page1.offset
    )
    assertThat(page2.hasMorePages).isFalse()
    assertThat(page2.trackTitles).containsAll(expectedTrackTitles.slice(20..24))
  }

  @Test
  fun localSecondaryIndexWithFilter() {
    musicTable.givenAlbums(THE_WALL)
    val expectedTrackTitles = THE_WALL.tracks
      .filter { it.run_length > Duration.ofMinutes(3) }
      .map { it.track_title }
      .sorted()

    val page1 = musicTable.albumTracksByTitle.scan(
      filterExpression = runLengthLongerThan(Duration.ofMinutes(3))
    )

    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.trackTitles).containsAll(expectedTrackTitles)
  }

  @Test
  fun globalSecondaryIndex() {
    musicTable.givenAlbums(
      THE_DARK_SIDE_OF_THE_MOON,
      THE_WALL,
      WHAT_YOU_DO_TO_ME_SINGLE,
      AFTER_HOURS_EP,
      LOCKDOWN_SINGLE
    )

    val page1 = musicTable.albumInfoByArtist.scan()

    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.albumTitles).containsExactly(
      AFTER_HOURS_EP.album_title,
      WHAT_YOU_DO_TO_ME_SINGLE.album_title,
      LOCKDOWN_SINGLE.album_title,
      THE_DARK_SIDE_OF_THE_MOON.album_title,
      THE_WALL.album_title
    )
  }

  @Test
  fun globalSecondaryIndexWithFilter() {
    musicTable.givenAlbums(
      THE_DARK_SIDE_OF_THE_MOON,
      THE_WALL,
      WHAT_YOU_DO_TO_ME_SINGLE,
      AFTER_HOURS_EP,
      LOCKDOWN_SINGLE
    )

    val page1 = musicTable.albumInfoByArtist.scan(
      filterExpression = releaseYearIs(2020)
    )

    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.albumTitles).containsExactly(
      AFTER_HOURS_EP.album_title,
      LOCKDOWN_SINGLE.album_title
    )
  }

  private fun releaseYearIs(year: Int): FilterExpression {
    return FilterExpression(
      "begins_with(release_date, :year)",
      mapOf(
        ":year" to AttributeValue().withS("$year")
      )
    )
  }

  private fun isTrack(): FilterExpression {
    return FilterExpression(
      "begins_with(sort_key, :track_prefix)",
      mapOf(
        ":track_prefix" to AttributeValue().withS("TRACK_")
      )
    )
  }

  private fun runLengthLongerThan(duration: Duration): FilterExpression {
    return FilterExpression(
      "run_length > :duration",
      mapOf(
        ":duration" to AttributeValue().withS(duration.toString())
      )
    )
  }
}
