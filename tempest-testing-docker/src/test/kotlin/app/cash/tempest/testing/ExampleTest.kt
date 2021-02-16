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

package app.cash.tempest.testing

import app.cash.tempest.urlshortener.Alias
import app.cash.tempest.urlshortener.AliasDb
import app.cash.tempest.urlshortener.AliasItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ExampleTest {

  @RegisterExtension
  @JvmField
  val db = testDb()

  private val aliasTable by lazy { db.logicalDb<AliasDb>().aliasTable }

  @Test
  fun test() {
    val alias = Alias(
      "SquareCLA",
      "https://docs.google.com/forms/d/e/1FAIpQLSeRVQ35-gq2vdSxD1kdh7CJwRdjmUA0EZ9gRXaWYoUeKPZEQQ/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1"
    )
    aliasTable.aliases.save(alias)
    val loadedAlias = aliasTable.aliases.load(alias.key)
    assertThat(loadedAlias).isNotNull()
    assertThat(loadedAlias!!.short_url).isEqualTo(alias.short_url)
    assertThat(loadedAlias.destination_url).isEqualTo(alias.destination_url)
  }
}

fun testDb() = TestDynamoDb.Builder(DockerDynamoDbServer)
  .addTable(TestTable.create<AliasItem>())
  .build()
