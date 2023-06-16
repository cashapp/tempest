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

package app.cash.tempest2

import app.cash.tempest.musiclibrary.AFTER_HOURS_EP
import app.cash.tempest.musiclibrary.LOCKDOWN_SINGLE
import app.cash.tempest.musiclibrary.THE_DARK_SIDE_OF_THE_MOON
import app.cash.tempest.musiclibrary.THE_WALL
import app.cash.tempest.musiclibrary.WHAT_YOU_DO_TO_ME_SINGLE
import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.AsyncMusicDb
import app.cash.tempest2.musiclibrary.MusicItem
import app.cash.tempest2.musiclibrary.albumTitles
import app.cash.tempest2.musiclibrary.givenAlbums
import app.cash.tempest2.musiclibrary.testDb
import app.cash.tempest2.musiclibrary.trackTitles
import app.cash.tempest2.testing.asyncLogicalDb
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DynamoDbAsyncScannableTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicTable by lazy { db.asyncLogicalDb<AsyncMusicDb>().music }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  internal fun `asynchronously, rows with missing attributes throw and do not hang forever`() {
    val enhancedClient = DynamoDbEnhancedAsyncClient.builder()
      .dynamoDbClient(db.asyncDynamoDb)
      .build()
    val mapper =
      enhancedClient.table(MusicItem.TABLE_NAME, TableSchema.fromBean(MusicItem::class.java))

    val albumInfoCodec = musicTable.codec(AlbumInfo::class)
    val albumInfo = AlbumInfo(
      "ALBUM_1",
      "after hours - EP",
      "53 Thieves",
      LocalDate.of(2020, 2, 21),
      "Contemporary R&B"
    )
    val musicItem = albumInfoCodec.toDb(albumInfo)

    // Attribute "genre_name" is nullable in MusicItem but is non-nullable in AlbumInfo.
    runBlocking {
      mapper.putItem(
        MusicItem().apply {
          partition_key = musicItem.partition_key
          sort_key = musicItem.sort_key
          album_title = musicItem.album_title
          artist_name = musicItem.artist_name
          release_date = musicItem.release_date
          genre_name = null
        }
      ).await()
    }

    // Conversion from MusicItem to AlbumInfo should throw an exception.
    assertThrows<ReflectiveOperationException> {
      runBlocking {
        musicTable.albumInfo.scan()
      }
    }
  }

  @Test
  fun primaryIndex() = runBlockingTest {
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
  fun localSecondaryIndex() = runBlockingTest {
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
  fun localSecondaryIndexWithFilter() = runBlockingTest {
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
  fun globalSecondaryIndex() = runBlockingTest {
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
  fun globalSecondaryIndexWithFilter() = runBlockingTest {
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

  private fun releaseYearIs(year: Int): Expression {
    return Expression.builder()
      .expression("begins_with(release_date, :year)")
      .expressionValues(
        mapOf(
          ":year" to AttributeValue.builder().s("$year").build()
        )
      )
      .build()
  }

  private fun isTrack(): Expression {
    return Expression.builder()
      .expression("begins_with(sort_key, :track_prefix)")
      .expressionValues(
        mapOf(
          ":track_prefix" to AttributeValue.builder().s("TRACK_").build()
        )
      )
      .build()
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
}
