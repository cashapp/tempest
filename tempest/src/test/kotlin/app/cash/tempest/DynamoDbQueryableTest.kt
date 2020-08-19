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
import app.cash.tempest.musiclibrary.AlbumInfo
import app.cash.tempest.musiclibrary.AlbumTrack
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
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbQueryableTest {
  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb

  private val musicTable get() = musicDb.music

  @Test
  fun primaryIndexBetween() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page1 = musicTable.albumTracks.query(
      keyCondition = Between(
        AlbumTrack.Key(AFTER_HOURS_EP.album_token, 1),
        AlbumTrack.Key(AFTER_HOURS_EP.album_token, 1)
      )
    )
    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(0..0))

    val page2 = musicTable.albumTracks.query(
      keyCondition = Between(
        AlbumTrack.Key(AFTER_HOURS_EP.album_token, 2),
        AlbumTrack.Key(AFTER_HOURS_EP.album_token, 3)
      )
    )
    assertThat(page2.hasMorePages).isFalse()
    assertThat(page2.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(1..2))

    val page3 = musicTable.albumTracks.query(
      keyCondition = Between(
        AlbumTrack.Key(AFTER_HOURS_EP.album_token, 1),
        AlbumTrack.Key(AFTER_HOURS_EP.album_token, 3)
      )
    )
    assertThat(page3.hasMorePages).isFalse()
    assertThat(page3.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(0..2))
  }

  @Test
  fun primaryIndexBeginsWith() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page1 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token))
    )
    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles)

    val page2 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, 3))
    )
    assertThat(page2.hasMorePages).isFalse()
    assertThat(page2.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(2..2))
  }

  @Test
  fun primaryIndexFilter() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page1 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token)),
      filterExpression = runLengthLongerThan(Duration.ofMinutes(3))
    )
    assertThat(page1.hasMorePages).isFalse()
    assertThat(page1.trackTitles).containsExactly(
      AFTER_HOURS_EP.trackTitles[0],
      AFTER_HOURS_EP.trackTitles[1],
      AFTER_HOURS_EP.trackTitles[4]
    )
  }

  @Test
  fun primaryIndexPagination() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page1 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      pageSize = 2
    )
    assertThat(page1.hasMorePages).isTrue()
    assertThat(page1.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(0..1))

    val page2 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      pageSize = 2,
      initialOffset = page1.offset
    )
    assertThat(page2.hasMorePages).isTrue()
    assertThat(page2.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(2..3))

    val page3 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      pageSize = 2,
      initialOffset = page2.offset
    )
    assertThat(page3.hasMorePages).isFalse()
    assertThat(page3.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(4..4))
  }

  @Test
  fun primaryIndexDesc() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      asc = false
    )
    assertThat(page.hasMorePages).isFalse()
    assertThat(page.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.reversed())
  }

  @Test
  fun primaryIndexDescPagination() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page1 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      asc = false,
      pageSize = 2
    )
    assertThat(page1.hasMorePages).isTrue()
    assertThat(page1.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.reversed().slice(0..1))

    val page2 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      asc = false,
      pageSize = 2,
      initialOffset = page1.offset
    )
    assertThat(page2.hasMorePages).isTrue()
    assertThat(page2.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.reversed().slice(2..3))

    val page3 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      asc = false,
      pageSize = 2,
      initialOffset = page2.offset
    )
    assertThat(page3.hasMorePages).isFalse()
    assertThat(page3.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.reversed().slice(4..4))
  }

  @Test
  fun localSecondaryIndex() {
    musicTable.givenAlbums(AFTER_HOURS_EP)
    val expectedTrackTitles = AFTER_HOURS_EP.trackTitles.sorted()

    val page = musicTable.albumTracksByTitle.query(
      keyCondition = BeginsWith(AlbumTrack.TitleIndexOffset(AFTER_HOURS_EP.album_token))
    )
    assertThat(page.hasMorePages).isFalse()
    assertThat(page.trackTitles).containsAll(expectedTrackTitles)
  }

  @Test
  fun localSecondaryIndexPagination() {
    musicTable.givenAlbums(AFTER_HOURS_EP)
    val expectedTrackTitles = AFTER_HOURS_EP.trackTitles.sorted()

    val page1 = musicTable.albumTracksByTitle.query(
      keyCondition = BeginsWith(AlbumTrack.TitleIndexOffset(AFTER_HOURS_EP.album_token)),
      pageSize = 2
    )
    assertThat(page1.hasMorePages).isTrue()
    assertThat(page1.trackTitles).containsAll(expectedTrackTitles.slice(0..1))

    val page2 = musicTable.albumTracksByTitle.query(
      keyCondition = BeginsWith(AlbumTrack.TitleIndexOffset(AFTER_HOURS_EP.album_token)),
      pageSize = 2,
      initialOffset = page1.offset
    )
    assertThat(page2.hasMorePages).isTrue()
    assertThat(page2.trackTitles).containsAll(expectedTrackTitles.slice(2..3))

    val page3 = musicTable.albumTracksByTitle.query(
      keyCondition = BeginsWith(AlbumTrack.TitleIndexOffset(AFTER_HOURS_EP.album_token)),
      pageSize = 2,
      initialOffset = page2.offset
    )
    assertThat(page3.hasMorePages).isFalse()
    assertThat(page3.trackTitles).containsAll(expectedTrackTitles.slice(4..4))
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
    val artist1Page = musicTable.albumInfoByArtist.query(
      BeginsWith(AlbumInfo.ArtistIndexOffset("Pink Floyd", ""))
    )
    assertThat(artist1Page.hasMorePages).isFalse()
    assertThat(artist1Page.albumTitles).containsExactly(
      THE_DARK_SIDE_OF_THE_MOON.album_title,
      THE_WALL.album_title
    )

    val artist2Page = musicTable.albumInfoByArtist.query(
      BeginsWith(AlbumInfo.ArtistIndexOffset("53 Theives", ""))
    )
    assertThat(artist2Page.hasMorePages).isFalse()
    assertThat(artist2Page.albumTitles).containsExactly(
      AFTER_HOURS_EP.album_title,
      WHAT_YOU_DO_TO_ME_SINGLE.album_title,
      LOCKDOWN_SINGLE.album_title
    )
  }

  @Test
  fun globalSecondaryIndexPagination() {
    musicTable.givenAlbums(
      WHAT_YOU_DO_TO_ME_SINGLE,
      AFTER_HOURS_EP,
      LOCKDOWN_SINGLE
    )
    val page1 = musicTable.albumInfoByArtist.query(
      BeginsWith(AlbumInfo.ArtistIndexOffset("53 Theives", "")),
      pageSize = 2
    )
    assertThat(page1.hasMorePages).isTrue()
    assertThat(page1.albumTitles).containsExactly(
      AFTER_HOURS_EP.album_title,
      WHAT_YOU_DO_TO_ME_SINGLE.album_title
    )

    val page2 = musicTable.albumInfoByArtist.query(
      BeginsWith(AlbumInfo.ArtistIndexOffset("53 Theives", "")),
      pageSize = 2,
      initialOffset = page1.offset
    )
    assertThat(page2.hasMorePages).isFalse()
    assertThat(page2.albumTitles).containsExactly(
      LOCKDOWN_SINGLE.album_title
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
