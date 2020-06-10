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

import app.cash.tempest.BeginsWith
import app.cash.tempest.Between
import app.cash.tempest.KeyCondition
import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.Queryable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BETWEEN
import com.amazonaws.services.dynamodbv2.model.Condition
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.amazonaws.services.dynamodbv2.model.Select.SPECIFIC_ATTRIBUTES
import kotlin.reflect.KClass

internal class DynamoDbQueryable<K : Any, I : Any>(
  private val hashKeyName: String,
  private val rangeKeyName: String,
  private val attributeNames: Set<String>,
  private val keyCodec: Codec<K, Any>,
  private val itemCodec: Codec<I, Any>,
  private val rawType: KClass<Any>,
  private val tableModel: DynamoDBMapperTableModel<Any>,
  private val dynamoDbMapper: DynamoDBMapper
) : Queryable<K, I> {

  override fun query(
    keyCondition: KeyCondition<K>,
    consistentRead: Boolean,
    asc: Boolean,
    pageSize: Int,
    returnConsumedCapacity: ReturnConsumedCapacity,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    val query = DynamoDBQueryExpression<Any>()
    query.apply(keyCondition)
    query.isScanIndexForward = asc
    query.isConsistentRead = consistentRead
    query.limit = pageSize
    query.withSelect(SPECIFIC_ATTRIBUTES)
    query.projectionExpression = attributeNames.joinToString(", ")
    if (initialOffset != null) {
      query.exclusiveStartKey = initialOffset.encodeOffset()
    }
    val page = dynamoDbMapper.queryPage(rawType.java, query)
    val contents = page.results.map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey?.decodeOffset()
    return Page(contents, offset, page.scannedCount, page.consumedCapacity)
  }

  private fun DynamoDBQueryExpression<Any>.apply(keyCondition: KeyCondition<K>) = apply {
    when (keyCondition) {
      is BeginsWith -> {
        val value = keyCodec.toDb(keyCondition.prefix)
        val valueAttributes = tableModel.convert(value)
        val hashKeyValue = tableModel.unconvert(mapOf(hashKeyName to valueAttributes[hashKeyName]))
        withHashKeyValues(hashKeyValue)
          .withRangeKeyCondition(
            rangeKeyName,
            Condition()
              .withComparisonOperator(BEGINS_WITH)
              .withAttributeValueList(valueAttributes[rangeKeyName]))
      }
      is Between -> {
        val start = keyCodec.toDb(keyCondition.startInclusive)
        val end = keyCodec.toDb(keyCondition.endInclusive)
        val startAttributes = tableModel.convert(start)
        val endAttributes = tableModel.convert(end)
        require(startAttributes[hashKeyName] == endAttributes[hashKeyName])
        val hashKeyValue = tableModel.unconvert(mapOf(hashKeyName to startAttributes[hashKeyName]))
        withHashKeyValues(hashKeyValue)
          .withRangeKeyCondition(
            rangeKeyName,
            Condition()
              .withComparisonOperator(BETWEEN)
              .withAttributeValueList(startAttributes[rangeKeyName], endAttributes[rangeKeyName]))
      }
    }
  }

  private fun Offset<K>.encodeOffset(): Map<String, AttributeValue> {
    val offsetKey = keyCodec.toDb(key)
    return tableModel.convert(offsetKey)
  }

  private fun Map<String, AttributeValue>.decodeOffset(): Offset<K> {
    val offsetKeyAttributes = tableModel.unconvert(this)
    val offsetKey = keyCodec.toApp(offsetKeyAttributes)
    return Offset(offsetKey)
  }
}
