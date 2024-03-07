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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.KeyType
import kotlin.reflect.KClass

internal object V1ForIndexAnnotation : ForIndexAnnotation<ForIndex> {
  override val type = ForIndex::class
  override fun name(annotation: ForIndex) = annotation.name
}

internal object V1AttributeAnnotation : AttributeAnnotation<Attribute> {
  override val type = Attribute::class
  override fun name(annotation: Attribute) = annotation.name
  override fun names(annotation: Attribute) = annotation.names
  override fun prefix(annotation: Attribute) = annotation.prefix
  override fun allowEmpty(annotation: Attribute) = false
}

internal object V1StringAttributeValue : StringAttributeValue<AttributeValue> {
  override fun toAttributeValue(s: String) = AttributeValue(s)
  override fun toString(attributeValue: AttributeValue) = attributeValue.s
}

internal class V1MapAttributeValue<DB : Any>(
  private val tableModel: DynamoDBMapperTableModel<DB>
) : MapAttributeValue<AttributeValue, DB> {
  override fun toAttributeValues(dbItem: DB) = tableModel.convert(dbItem)
  override fun toDb(attributeValues: Map<String, AttributeValue>) = tableModel.unconvert(attributeValues)

  class Factory(
    private val dynamoDbMapper: DynamoDBMapper
  ) : MapAttributeValue.Factory {
    override fun <T : Any, DB : Any> create(type: KClass<DB>): MapAttributeValue<T, DB> {
      return V1MapAttributeValue(dynamoDbMapper.getTableModel(type.java)) as MapAttributeValue<T, DB>
    }
  }
}

internal class V1RawItemTypeFactory(
  private val dynamoDbMapper: DynamoDBMapper
) : RawItemType.Factory {

  override fun create(tableName: String, rawItemType: KClass<*>): RawItemType {
    val tableModel = dynamoDbMapper.getTableModel(rawItemType.java) as DynamoDBMapperTableModel<Any>
    return RawItemType(
      rawItemType as KClass<Any>,
      tableName,
      tableModel.hashKey<Any>().name(),
      tableModel.rangeKeyIfExists<Any>()?.name(),
      tableModel.fields().map { it.name() }.sorted(),
      secondaryIndexes(tableModel)
    )
  }

  private fun secondaryIndexes(
    tableModel: DynamoDBMapperTableModel<Any>
  ): Map<String, ItemType.SecondaryIndex> {
    val secondaryIndexes = mutableMapOf<String, ItemType.SecondaryIndex>()
    val globalSecondaryIndexes = tableModel.globalSecondaryIndexes() ?: emptyList()
    for (globalSecondaryIndex in globalSecondaryIndexes) {
      val indexName = globalSecondaryIndex.indexName
      val keys = globalSecondaryIndex.keySchema.associateBy { it.keyType }
      val hashKeyName = requireNotNull(keys[KeyType.HASH.toString()]).attributeName
      val rangeKeyName = keys.get(KeyType.RANGE.toString())?.attributeName
      secondaryIndexes[indexName] = ItemType.SecondaryIndex(indexName, hashKeyName, rangeKeyName)
    }
    val localSecondaryIndexes = tableModel.localSecondaryIndexes() ?: emptyList()
    for (localSecondaryIndex in localSecondaryIndexes) {
      val indexName = localSecondaryIndex.indexName
      val keys = localSecondaryIndex.keySchema.associateBy { it.keyType }
      val hashKeyName = requireNotNull(keys[KeyType.HASH.toString()]).attributeName
      val rangeKeyName = keys[KeyType.RANGE.toString()]?.attributeName
      secondaryIndexes[indexName] = ItemType.SecondaryIndex(indexName, hashKeyName, rangeKeyName)
    }
    return secondaryIndexes.toMap()
  }
}
