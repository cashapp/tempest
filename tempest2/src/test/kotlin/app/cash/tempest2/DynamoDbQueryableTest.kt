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
import app.cash.tempest.musiclibrary.SPIRIT_WORLD_FIELD_GUIDE
import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.AlbumTrack
import app.cash.tempest2.musiclibrary.MusicDb
import app.cash.tempest2.musiclibrary.MusicItem
import app.cash.tempest2.musiclibrary.albumTitles
import app.cash.tempest2.musiclibrary.givenAlbums
import app.cash.tempest2.musiclibrary.testDb
import app.cash.tempest2.musiclibrary.trackTitles
import app.cash.tempest2.testing.logicalDb
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity
import java.time.Duration
import java.time.LocalDate

class DynamoDbQueryableTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicTable by lazy { db.logicalDb<MusicDb>().music }

  @Test
  internal fun `synchronously, rows with missing attributes throw`() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(db.dynamoDb)
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
    mapper.putItem(
      MusicItem().apply {
        partition_key = musicItem.partition_key
        sort_key = musicItem.sort_key
        album_title = musicItem.album_title
        artist_name = musicItem.artist_name
        release_date = musicItem.release_date
        genre_name = null
      }
    )

    // Conversion from MusicItem to AlbumInfo should throw an exception.
    assertThrows<ReflectiveOperationException> {
      musicTable.albumInfo.query(BeginsWith(albumInfo.key))
    }
  }

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
      LOCKDOWN_SINGLE,
      SPIRIT_WORLD_FIELD_GUIDE
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
    assertThat(page1.contents.map { it.label_name }.filterNotNull().size).isEqualTo(0)

    val page2 = musicTable.albumInfoByArtist.query(
      BeginsWith(AlbumInfo.ArtistIndexOffset("53 Theives", "")),
      pageSize = 2,
      initialOffset = page1.offset
    )
    assertThat(page2.hasMorePages).isFalse()
    assertThat(page2.albumTitles).containsExactly(
      LOCKDOWN_SINGLE.album_title
    )
    assertThat(page2.contents.map { it.label_name }.filterNotNull().size).isEqualTo(0)

    val sparseGsiPage = musicTable.albumInfoByLabel.query(
      BeginsWith(AlbumInfo.LabelIndexOffset("Rhymesayers", "")),
      pageSize = 1
    )

    assertThat(sparseGsiPage.hasMorePages).isTrue()
    assertThat(sparseGsiPage.albumTitles).containsExactly(
      SPIRIT_WORLD_FIELD_GUIDE.album_title
    )
    assertThat(sparseGsiPage.contents.single().label_name).isEqualTo(SPIRIT_WORLD_FIELD_GUIDE.label)
  }

  @Test
  fun consumedCapacity() {
    musicTable.givenAlbums(AFTER_HOURS_EP)

    val page1 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      pageSize = 2,
      returnConsumedCapacity = ReturnConsumedCapacity.TOTAL
    )
    assertThat(page1.hasMorePages).isTrue()
    assertThat(page1.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(0..1))
    assertThat(page1.consumedCapacity).isNotNull

    val page2 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      pageSize = 2,
      initialOffset = page1.offset,
      returnConsumedCapacity = ReturnConsumedCapacity.TOTAL
    )
    assertThat(page2.hasMorePages).isTrue()
    assertThat(page2.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(2..3))
    assertThat(page2.consumedCapacity).isNotNull

    val page3 = musicTable.albumTracks.query(
      keyCondition = BeginsWith(AlbumTrack.Key(AFTER_HOURS_EP.album_token, "")),
      pageSize = 2,
      initialOffset = page2.offset,
      returnConsumedCapacity = ReturnConsumedCapacity.INDEXES
    )
    assertThat(page3.hasMorePages).isFalse()
    assertThat(page3.trackTitles).containsAll(AFTER_HOURS_EP.trackTitles.slice(4..4))
    assertThat(page3.consumedCapacity).isNotNull
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
