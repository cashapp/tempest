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

package app.cash.tempest2.urlshortener

import app.cash.tempest2.InlineView
import app.cash.tempest2.LogicalTable

interface AliasTable : LogicalTable<AliasItem> {
  val aliases: InlineView<Alias.Key, Alias>
}

data class Alias(
  val short_url: String,
  val destination_url: String
) {
  @Transient
  val key = Key(short_url)

  data class Key(
    val short_url: String
  )
}
