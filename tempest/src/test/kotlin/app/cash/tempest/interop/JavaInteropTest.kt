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

package app.cash.tempest.interop

import app.cash.tempest.InlineView
import javax.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class JavaInteropTest {

  @MiskTestModule
  val module = InteropTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var jAliasDb: JAliasDb
  @Inject lateinit var kAliasDb: KAliasDb

  @Test
  fun javaLogicalTypeJavaItemType() {
    jAliasDb.aliasTable().jAliases().loadAfterSave(JALIAS)
  }

  @Test
  fun javaLogicalTypeKotlinItemType() {
    jAliasDb.aliasTable().kAliases().loadAfterSave(KALIAS)
  }

  @Test
  fun kotlinLogicalTypeKotlinItemType() {
    kAliasDb.aliasTable.kAliases.loadAfterSave(KALIAS)
  }

  @Test
  fun kotlinLogicalTypeJavaItemType() {
    kAliasDb.aliasTable.jAliases.loadAfterSave(JALIAS)
  }

  private fun InlineView<JAlias.Key, JAlias>.loadAfterSave(alias: JAlias) {
    save(alias)
    val loadedAlias = load(alias.key())!!
    assertThat(loadedAlias.short_url).isEqualTo(alias.short_url)
    assertThat(loadedAlias.destination_url).isEqualTo(alias.destination_url)
  }

  private fun InlineView<KAlias.Key, KAlias>.loadAfterSave(alias: KAlias) {
    save(alias)
    val loadedAlias = load(alias.key)!!
    assertThat(loadedAlias.short_url).isEqualTo(alias.short_url)
    assertThat(loadedAlias.destination_url).isEqualTo(alias.destination_url)
  }

  companion object {
    val JALIAS = JAlias(
      "SquareCLA",
      "https://docs.google.com/forms/d/e/1FAIpQLSeRVQ35-gq2vdSxD1kdh7CJwRdjmUA0EZ9gRXaWYoUeKPZEQQ/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1"
    )
    val KALIAS = KAlias(
      "SquareCLA",
      "https://docs.google.com/forms/d/e/1FAIpQLSeRVQ35-gq2vdSxD1kdh7CJwRdjmUA0EZ9gRXaWYoUeKPZEQQ/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1"
    )
  }
}
