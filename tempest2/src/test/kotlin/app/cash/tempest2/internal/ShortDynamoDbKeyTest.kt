/*
 * Copyright 2026 Square Inc.
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

package app.cash.tempest2.internal

import app.cash.tempest.internal.Schema
import app.cash.tempest2.Attribute
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey

class ShortDynamoDbKeyTest {

  @Test
  fun fullPropertyNamesMapToShortDynamoDbKeys() {
    val schema = Schema.create(
      V2StringAttributeValue,
      V2MapAttributeValue.Factory,
      V2ForIndexAnnotation,
      V2AttributeAnnotation,
      V2RawItemTypeFactory()
    )
    schema.addRawItem("short_keys", ShortKeyItem::class)
    schema.addItem(ShortKeyView::class, ShortKeyItem::class)
    schema.addKey(ShortKeyView.Key::class, ShortKeyView::class)

    val rawItem = schema.codec<ShortKeyView.Key, ShortKeyItem>(ShortKeyView.Key::class)
      .toDb(ShortKeyView.Key(partitionKey = "account"))

    assertThat(rawItem.partitionKey).isEqualTo("account")
    assertThat(rawItem.sortKey).isEqualTo("item#")
    assertThat(TableSchemaFactory.create<ShortKeyItem>().itemToMap(rawItem, true).keys)
      .containsExactlyInAnyOrder("pk", "sk")
  }
}

@DynamoDbBean
class ShortKeyItem {
  @get:DynamoDbPartitionKey
  @get:DynamoDbAttribute("pk")
  var partitionKey: String? = null

  @get:DynamoDbSortKey
  @get:DynamoDbAttribute("sk")
  var sortKey: String? = null
}

data class ShortKeyView(
  val partitionKey: String
) {
  @Attribute(prefix = "item#")
  val sortKey: String = ""

  data class Key(
    val partitionKey: String
  ) {
    val sortKey: String = ""
  }
}
