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

package app.cash.tempest.example

import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

interface ArtistTable : LogicalTable<DyArtist> {
  val artists: InlineView<ArtistKey, Artist>
}

data class ArtistKey(
  val artist_token: String
)

data class Artist(
  val artist_token: String,
  val name: String
)

@DynamoDBTable(tableName = "artists")
class DyArtist {
  // All Rows.
  @DynamoDBHashKey
  var artist_token: String? = null
  @DynamoDBAttribute
  var name: String? = null
}
