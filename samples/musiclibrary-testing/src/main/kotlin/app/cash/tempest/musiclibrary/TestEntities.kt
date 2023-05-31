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

package app.cash.tempest.musiclibrary

import java.time.Duration
import java.time.LocalDate

val THE_DARK_SIDE_OF_THE_MOON = Album(
  album_token = "ALBUM_cafcf892",
  album_title = "The Dark Side of the Moon",
  artist_name = "Pink Floyd",
  release_date = LocalDate.of(1973, 3, 1),
  genre_name = "Progressive rock",
  tracks = listOf(
    Track(
      "Speak to Me",
      Duration.parse("PT1M13S")
    ),
    Track("Breathe", Duration.parse("PT2M43S")),
    Track("On the Run", Duration.parse("PT3M36S")),
    Track("Time", Duration.parse("PT6M53S")),
    Track(
      "The Great Gig in the Sky",
      Duration.parse("PT4M36S")
    ),
    Track("Money", Duration.parse("PT6M23S")),
    Track(
      "Us and Them",
      Duration.parse("PT7M49S")
    ),
    Track(
      "Any Colour You Like",
      Duration.parse("PT3M26S")
    ),
    Track(
      "Brain Damage",
      Duration.parse("PT3M49S")
    ),
    Track("Eclipse", Duration.parse("PT2M3S"))
  )
)
val THE_WALL = Album(
  album_token = "ALBUM_ef17ab2c",
  album_title = "The Wall",
  artist_name = "Pink Floyd",
  release_date = LocalDate.of(1973, 3, 1),
  genre_name = "Progressive rock",
  tracks = listOf(
    Track(
      "In the Flesh?",
      Duration.parse("PT3M16S")
    ),
    Track(
      "The Thin Ice",
      Duration.parse("PT2M27S")
    ),
    Track(
      "Another Brick in the Wall, Part 1",
      Duration.parse("PT3M11S")
    ),
    Track(
      "The Happiest Days of Our Lives",
      Duration.parse("PT1M46S")
    ),
    Track(
      "Another Brick in the Wall, Part 2",
      Duration.parse("PT3M59S")
    ),
    Track("Mother", Duration.parse("PT5M32S")),
    Track(
      "Goodbye Blue Sky",
      Duration.parse("PT2M45S")
    ),
    Track(
      "Empty Spaces",
      Duration.parse("PT2M10S")
    ),
    Track("Young Lust", Duration.parse("PT3M25S")),
    Track(
      "One of My Turns",
      Duration.parse("PT3M41S")
    ),
    Track(
      "Don't Leave Me Now",
      Duration.parse("PT4M08S")
    ),
    Track(
      "Another Brick in the Wall, Part 3",
      Duration.parse("PT1M18S")
    ),
    Track(
      "Goodbye Cruel World",
      Duration.parse("PT1M16S")
    ),
    Track("Hey You", Duration.parse("PT4M40S")),
    Track(
      "Is There Anybody Out There?",
      Duration.parse("PT2M44S")
    ),
    Track(
      "Nobody Home",
      Duration.parse("PT3M26S")
    ),
    Track("Vera", Duration.parse("PT1M35S")),
    Track(
      "Bring the Boys Back Home",
      Duration.parse("PT1M21S")
    ),
    Track(
      "Comfortably Numb",
      Duration.parse("PT6M23S")
    ),
    Track(
      "The Show Must Go On",
      Duration.parse("PT1M36S")
    ),
    Track(
      "In the Flesh",
      Duration.parse("PT4M15S")
    ),
    Track(
      "Run Like Hell",
      Duration.parse("PT4M20S")
    ),
    Track(
      "Waiting for the Worms",
      Duration.parse("PT4M04S")
    ),
    Track("Stop", Duration.parse("PT0M30S")),
    Track("The Trial", Duration.parse("PT5M13S")),
    Track(
      "Outside the Wall",
      Duration.parse("PT1M41S")
    )
  )
)

val WHAT_YOU_DO_TO_ME_SINGLE = Album(
  album_token = "ALBUM_f464260d",
  album_title = "what you do to me - Single",
  artist_name = "53 Theives",
  release_date = LocalDate.of(2019, 8, 28),
  genre_name = "Contemporary R&B",
  tracks = listOf(
    Track(
      "what you do to me",
      Duration.parse("PT3M23S")
    )
  )
)

val AFTER_HOURS_EP = Album(
  album_token = "ALBUM_ba0fc195",
  album_title = "after hours - EP",
  artist_name = "53 Theives",
  release_date = LocalDate.of(2020, 2, 21),
  genre_name = "Contemporary R&B",
  tracks = listOf(
    Track("dreamin'", Duration.parse("PT3M28S")),
    Track(
      "what you do to me",
      Duration.parse("PT3M23S")
    ),
    Track("too slow", Duration.parse("PT2M36S")),
    Track("three a.m.", Duration.parse("PT2M40S")),
    Track("heat", Duration.parse("PT3M13S"))
  )
)

val LOCKDOWN_SINGLE = Album(
  album_token = "ALBUM_fae2a106",
  album_title = "Lockdown - Single",
  artist_name = "53 Theives",
  release_date = LocalDate.of(2020, 5, 15),
  genre_name = "Hip-Hop/Rap",
  tracks = listOf(
    Track("Lockdown'", Duration.parse("PT3M18S"))
  )
)

val SPIRIT_WORLD_FIELD_GUIDE = Album(
  album_token = "ALBUM_a9d2b95a",
  album_title = "Spirit World Field Guide",
  artist_name = "Aesop Rock",
  release_date = LocalDate.of(2020, 11, 13),
  genre_name = "Hip-Hop",
  tracks = listOf(
    Track("Hello from the Spirit World", Duration.parse("PT2M06S")),
    Track("The Gates", Duration.parse("PT3M51S")),
    Track("Button Masher", Duration.parse("PT3M45S")),
    Track("Dog at the Door", Duration.parse("PT1M28S")),
    Track("Gauze", Duration.parse("PT3M12S")),
    Track("Pizza Alley", Duration.parse("PT4M36S")),
    Track("Crystal Sword", Duration.parse("PT2M20S")),
    Track("Boot Soup", Duration.parse("PT4M08S")),
    Track("Coveralls", Duration.parse("PT3M39S")),
    Track("Jumping Coffin", Duration.parse("PT3M29S")),
    Track("Holy Waterfall", Duration.parse("PT3M48S")),
    Track("Flies", Duration.parse("PT0M46S")),
    Track("Salt", Duration.parse("PT3M37S")),
    Track("Sleeper Car", Duration.parse("PT3M42S")),
    Track("1 to 10", Duration.parse("PT0M53S")),
    Track("Attaboy", Duration.parse("PT3M00S")),
    Track("Kodokushi", Duration.parse("PT3M53S")),
    Track("Fixed and Dilated", Duration.parse("PT2M38S")),
    Track("Side Quest", Duration.parse("PT1M21S")),
    Track("Marble Cake", Duration.parse("PT4M16S")),
    Track("The Four Winds", Duration.parse("PT2M50S")),
    ),
  label = "Rhymesayers"
)

data class Album(
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val release_date: LocalDate,
  val genre_name: String,
  val tracks: List<Track>,
  val label: String? = null
) {
  val trackTitles = tracks.map { it.track_title }
}

data class Track(val track_title: String, val run_length: Duration)
