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

package app.cash.tempest2.urlshortener

import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException

class RealUrlShortener(
  private val table: AliasTable
) : UrlShortener {

  override fun shorten(shortUrl: String, destinationUrl: String): Boolean {
    val item = Alias(shortUrl, destinationUrl)
    val ifNotExist = Expression.builder()
      .expression("attribute_not_exists(short_url)")
      .build()
    return try {
      table.aliases.save(item, ifNotExist)
      true
    } catch (e: ConditionalCheckFailedException) {
      println("Failed to shorten $shortUrl because it already exists!")
      false
    }
  }

  override fun redirect(shortUrl: String): String? {
    val key = Alias.Key(shortUrl)
    return table.aliases.load(key)?.destination_url
  }
}
