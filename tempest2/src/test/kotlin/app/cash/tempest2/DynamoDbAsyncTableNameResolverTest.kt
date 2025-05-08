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

import app.cash.tempest2.musiclibrary.AlbumInfo
import app.cash.tempest2.musiclibrary.AsyncMusicDb
import app.cash.tempest2.musiclibrary.AsyncMusicTable
import app.cash.tempest2.musiclibrary.testDb
import app.cash.tempest2.testing.asyncLogicalDb
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class DynamoDbAsyncTableNameResolverTest {

  @RegisterExtension
  @JvmField
  val db = testDb("custom_table_name")

  private object TestTableNameResolver : TableNameResolver {
    override fun resolveTableName(clazz: Class<*>, tableNameFromAnnotation: String?): String {
      check(clazz == AsyncMusicTable::class.java)

      return "custom_table_name"
    }
  }

  private val musicTable = db.asyncLogicalDb<AsyncMusicDb>(extensions = listOf(), TestTableNameResolver).music

  @Test
  fun loadAfterSave() = runBlockingTest {
    val albumInfo = AlbumInfo(
      "ALBUM_1",
      "after hours - EP",
      "53 Thieves",
      LocalDate.of(2020, 2, 21),
      "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo)

    // Query the movies created.
    val loadedAlbumInfo = musicTable.albumInfo.load(albumInfo.key)!!

    assertThat(loadedAlbumInfo.album_token).isEqualTo(albumInfo.album_token)
    assertThat(loadedAlbumInfo.artist_name).isEqualTo(albumInfo.artist_name)
    assertThat(loadedAlbumInfo.release_date).isEqualTo(albumInfo.release_date)
    assertThat(loadedAlbumInfo.genre_name).isEqualTo(albumInfo.genre_name)
  }
}
