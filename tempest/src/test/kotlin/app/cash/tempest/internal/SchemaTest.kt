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

package app.cash.tempest.internal

import app.cash.tempest.Attribute
import app.cash.tempest.ForIndex
import app.cash.tempest.example.AlbumInfo
import app.cash.tempest.example.MusicDbTestModule
import app.cash.tempest.example.TestDb
import java.time.LocalDate
import javax.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class SchemaTest {

  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var testDb: TestDb

  @Test
  fun badKeyType() {
    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset1::class, AlbumInfo::class)
    }.withMessageContaining("partition_key")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset2::class, AlbumInfo::class)
    }.withMessageContaining("sort_key")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset3::class, AlbumInfo::class)
    }.withMessageContaining("artist_name")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset4::class, AlbumInfo::class)
    }.withMessageContaining("nonexistent_attribute")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset5::class, AlbumInfo::class)
    }.withMessageContaining("nonexistent_index_name")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset6::class, AlbumInfo::class)
    }.withMessageContaining("sort_key")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.secondaryIndex(BadIndexOffset7::class, AlbumInfo::class)
    }.withMessageContaining("constructor")
  }

  @Test
  fun badItemType() {
    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem1::class)
    }.withMessageContaining("partition_key")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem2::class)
    }.withMessageContaining("sort_key")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem3::class)
    }.withMessageContaining("ambiguous")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem4::class)
    }.withMessageContaining("album_title")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem5::class)
    }.withMessageContaining("nonexistent_attribute")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem6::class)
    }.withMessageContaining("prefix")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem7::class)
    }.withMessageContaining("constructor")

    assertThatIllegalArgumentException().isThrownBy {
      testDb.music.inlineView(Any::class, BadItem8::class)
    }.withMessageContainingAll("mapped properties", "album_title", "playlist_size")
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
