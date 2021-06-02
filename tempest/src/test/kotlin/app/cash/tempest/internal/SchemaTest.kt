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

package app.cash.tempest.internal

import app.cash.tempest.Attribute
import app.cash.tempest.ForIndex
import app.cash.tempest.musiclibrary.AlbumInfo
import app.cash.tempest.musiclibrary.MusicDb
import app.cash.tempest.musiclibrary.testDb
import app.cash.tempest.testing.logicalDb
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate

class SchemaTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val musicDb by lazy { db.logicalDb<MusicDb>() }

  @Test
  fun badKeyType() {
    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset1::class, AlbumInfo::class)
    }.withMessage("Expect class app.cash.tempest.internal.BadIndexOffset1 to have property album_token")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset2::class, AlbumInfo::class)
    }.withMessage("Expect class app.cash.tempest.internal.BadIndexOffset2 to have property sort_key")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset3::class, AlbumInfo::class)
    }.withMessage("Expect the return type of class app.cash.tempest.internal.BadIndexOffset3.artist_name to be kotlin.String but was kotlin.Int")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset4::class, AlbumInfo::class)
    }.withMessage("Expect nonexistent_attribute, required by class app.cash.tempest.internal.BadIndexOffset4, to be declared in class app.cash.tempest.musiclibrary.AlbumInfo. But found [album_title, album_token, artist_name, genre_name, release_date, sort_key]. Use @Transient to exclude it.")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset5::class, AlbumInfo::class)
    }.withMessage("Expect class app.cash.tempest.musiclibrary.MusicItem to have secondary index nonexistent_index_name")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset6::class, AlbumInfo::class)
    }.withMessage("Please move Attribute annotation from class app.cash.tempest.internal.BadIndexOffset6.sort_key to class app.cash.tempest.musiclibrary.AlbumInfo.sort_key")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.secondaryIndex(BadIndexOffset7::class, AlbumInfo::class)
    }.withMessage("class app.cash.tempest.internal.BadIndexOffset7 must have a constructor")
  }

  @Test
  fun badItemType() {
    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem1::class)
    }.withMessage("Expect class app.cash.tempest.internal.BadItem1 to map to class app.cash.tempest.musiclibrary.MusicItem's hash key partition_key")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem2::class)
    }.withMessage("Expect class app.cash.tempest.internal.BadItem2 to map to class app.cash.tempest.musiclibrary.MusicItem's range key sort_key")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem3::class)
    }.withMessage("Attribute annotation is ambiguous. name: partition_key, names: [partition_key]")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem4::class)
    }.withMessage("Expect the return type of class app.cash.tempest.internal.BadItem4.album_title to be kotlin.String but was kotlin.Int")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem5::class)
    }.withMessage("Expect nonexistent_attribute, required by class app.cash.tempest.internal.BadItem5, to be declared in class app.cash.tempest.musiclibrary.MusicItem. But found [album_title, artist_name, genre_name, partition_key, playlist_name, playlist_size, playlist_tracks, playlist_version, release_date, run_length, sort_key, track_title, track_token]. Use @Transient to exclude it.")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem6::class)
    }.withMessage("Expect class app.cash.tempest.internal.BadItem6.sort_key to be annotated with a prefix")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem7::class)
    }.withMessage("class app.cash.tempest.internal.BadItem7 must have a constructor")

    assertThatIllegalArgumentException().isThrownBy {
      musicDb.music.inlineView(Any::class, BadItem8::class)
    }.withMessage("Expect mapped properties of album_title to have the same type: [album_title, playlist_size]")
  }
}

@ForIndex("artist_album_index")
data class BadIndexOffset1(
  val artist_name: String,
  // Missing partition_key.
  val sort_key: String? = null
)

@ForIndex("artist_album_index")
data class BadIndexOffset2(
  val artist_name: String,
  val album_token: String? = null
  // Missing sort_key.
)

@ForIndex("artist_album_index")
data class BadIndexOffset3(
  val artist_name: Int, // Bad attribute type.
  val album_token: String? = null,
  val sort_key: String? = null
)

@ForIndex("artist_album_index")
data class BadIndexOffset4(
  val nonexistent_attribute: String, // Bad attribute mame.
  val album_token: String? = null,
  val sort_key: String? = null
)

@ForIndex("nonexistent_index_name") // Bad index name.
data class BadIndexOffset5(
  val artist_name: String,
  val album_token: String? = null,
  val sort_key: String? = null
)

@ForIndex("artist_album_index")
data class BadIndexOffset6(
  val artist_name: String,
  val album_token: String? = null,
  @Attribute // @Attribute should be declared in the item type.
  val sort_key: String? = null
)

@ForIndex("artist_album_index")
interface BadIndexOffset7 { // Must have a constructor.
  val artist_name: String
  val album_token: String?
  val sort_key: String?
}

data class BadItem1(
  // Missing partition_key.
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""
}

data class BadItem2(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  // Missing sort_key.
}

data class BadItem3(
  // Ambiguous name.
  @Attribute(name = "partition_key", names = ["partition_key"])
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""
}

data class BadItem4(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: Int, // Bad attribute type.
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""
}

data class BadItem5(
  @Attribute(name = "partition_key")
  val album_token: String,
  val nonexistent_attribute: String, // Bad attribute name.
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""
}

data class BadItem6(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  // Sort key attributes must have a prefix.
  val sort_key: String = ""
}

interface BadItem7 { // Must have a constructor.
  @Attribute(name = "partition_key")
  val album_token: String
  val album_title: String
  val artist_name: String
  val release_date: LocalDate
  val genre_name: String

  @Attribute(prefix = "INFO_")
  val sort_key: String
}

data class BadItem8(
  @Attribute(name = "partition_key")
  val album_token: String,
  // `album_title`'s type and `playlist_size` type must agree.
  @Attribute(names = ["album_title", "playlist_size"])
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""
}
