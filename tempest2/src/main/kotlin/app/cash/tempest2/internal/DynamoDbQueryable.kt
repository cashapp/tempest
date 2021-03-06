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

import app.cash.tempest.internal.Codec
import app.cash.tempest2.BeginsWith
import app.cash.tempest2.Between
import app.cash.tempest2.KeyCondition
import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import app.cash.tempest2.Queryable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata
import software.amazon.awssdk.enhanced.dynamodb.internal.EnhancedClientUtils
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal class DynamoDbQueryable<K : Any, I : Any, R : Any>(
  private val secondaryIndexName: String?,
  private val specificAttributeNames: Set<String>,
  private val keyCodec: Codec<K, R>,
  private val itemCodec: Codec<I, R>,
  private val dynamoDbTable: DynamoDbTable<R>
) : Queryable<K, I> {

  override fun query(
    keyCondition: KeyCondition<K>,
    asc: Boolean,
    pageSize: Int,
    consistentRead: Boolean,
    filterExpression: Expression?,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    val query = QueryEnhancedRequest.builder()
      .queryConditional(toQueryConditional(keyCondition))
      .scanIndexForward(asc)
      .consistentRead(consistentRead)
      .limit(pageSize)
      .attributesToProject(specificAttributeNames)
    if (filterExpression != null) {
      query.filterExpression(filterExpression)
    }
    if (initialOffset != null) {
      query.exclusiveStartKey(initialOffset.encodeOffset())
    }
    val page = if (secondaryIndexName != null) {
      dynamoDbTable.index(secondaryIndexName).query(query.build())
    } else {
      dynamoDbTable.query(query.build())
    }
      .iterator().next()
    val contents = page.items().map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey()?.decodeOffset()
    return Page(contents, offset)
  }

  private fun toQueryConditional(keyCondition: KeyCondition<K>): QueryConditional {
    return when (keyCondition) {
      is BeginsWith -> {
        val value = keyCodec.toDb(keyCondition.prefix)
        val key = value.key()
        val hasSortKey = if (key.sortKeyValue().isPresent) {
          key.sortKeyValue().get().s()?.isNotEmpty() ?: true
        } else {
          false
        }
        if (hasSortKey) {
          QueryConditional.sortBeginsWith(key)
        } else {
          QueryConditional.keyEqualTo(
            Key.builder()
              .partitionValue(key.partitionKeyValue())
              .build()
          )
        }
      }
      is Between -> {
        val start = keyCodec.toDb(keyCondition.startInclusive)
        val end = keyCodec.toDb(keyCondition.endInclusive)
        QueryConditional.sortBetween(start.key(), end.key())
      }
    }
  }

  private fun Offset<K>.encodeOffset(): Map<String, AttributeValue> {
    val offsetKey = keyCodec.toDb(key)
    return dynamoDbTable.tableSchema().itemToMap(offsetKey, true)
  }

  private fun Map<String, AttributeValue>.decodeOffset(): Offset<K> {
    val offsetKeyAttributes = dynamoDbTable.tableSchema().mapToItem(this)
    val offsetKey = keyCodec.toApp(offsetKeyAttributes)
    return Offset(offsetKey)
  }

  private fun R.key(): Key {
    return EnhancedClientUtils.createKeyFromItem(
      this, dynamoDbTable.tableSchema(),
      secondaryIndexName ?: TableMetadata.primaryIndexName()
    )
  }
}
