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

package app.cash.tempest2.internal

import app.cash.tempest.internal.AttributeAnnotation
import app.cash.tempest.internal.ForIndexAnnotation
import app.cash.tempest.internal.ItemType
import app.cash.tempest.internal.MapAttributeValue
import app.cash.tempest.internal.RawItemType
import app.cash.tempest.internal.StringAttributeValue
import app.cash.tempest2.Attribute
import app.cash.tempest2.ForIndex
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import kotlin.reflect.KClass

internal object V2ForIndexAnnotation : ForIndexAnnotation<ForIndex> {
  override val type = ForIndex::class
  override fun name(annotation: ForIndex) = annotation.name
}

internal object V2AttributeAnnotation : AttributeAnnotation<Attribute> {
  override val type = Attribute::class
  override fun name(annotation: Attribute) = annotation.name
  override fun names(annotation: Attribute) = annotation.names
  override fun prefix(annotation: Attribute) = annotation.prefix
}

internal object V2StringAttributeValue : StringAttributeValue<AttributeValue> {
  override fun toAttributeValue(s: String) = AttributeValue.builder().s(s).build()
  override fun toString(attributeValue: AttributeValue) = attributeValue.s()
}

internal class V2MapAttributeValue<DB : Any>(
  private val tableSchema: TableSchema<DB>
) : MapAttributeValue<AttributeValue, DB> {
  override fun toAttributeValues(dbItem: DB) = tableSchema.itemToMap(dbItem, true)
  override fun toDb(attributeValue: Map<String, AttributeValue>) =
    tableSchema.mapToItem(attributeValue)

  object Factory : MapAttributeValue.Factory {
    override fun <T : Any, DB : Any> create(type: KClass<DB>): MapAttributeValue<T, DB> {
      return V2MapAttributeValue(TableSchema.fromClass(type.java)) as MapAttributeValue<T, DB>
    }
  }
}

internal class V2RawItemTypeFactory : RawItemType.Factory {

  override fun create(tableName: String, rawItemType: KClass<*>): RawItemType {
    val tableSchema = TableSchema.fromClass(rawItemType.java) as TableSchema<Any>
    return RawItemType(
      rawItemType as KClass<Any>,
      tableName,
      tableSchema.tableMetadata().primaryPartitionKey(),
      tableSchema.tableMetadata().primarySortKey().orElse(null),
      tableSchema.attributeNames(),
      tableSchema.tableMetadata().indices()
        .filter { it.name() != TableMetadata.primaryIndexName() }
        .map {
          ItemType.SecondaryIndex(
            it.name(),
            it.partitionKey().orElse(null)?.name() ?: tableSchema.tableMetadata().primaryPartitionKey(),
            it.sortKey().get().name()
          )
        }.associateBy { it.name }
    )
  }
}
