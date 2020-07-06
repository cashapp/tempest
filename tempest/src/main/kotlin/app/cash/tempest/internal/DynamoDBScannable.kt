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

import app.cash.tempest.FilterExpression
import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.Scannable
import app.cash.tempest.WorkerId
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.amazonaws.services.dynamodbv2.model.Select
import kotlin.reflect.KClass

internal class DynamoDBScannable<K : Any, I : Any>(
  private val secondaryIndexName: String?,
  private val attributeNames: Set<String>,
  private val keyCodec: Codec<K, Any>,
  private val itemCodec: Codec<I, Any>,
  private val rawType: KClass<Any>,
  private val tableModel: DynamoDBMapperTableModel<Any>,
  private val dynamoDbMapper: DynamoDBMapper
) : Scannable<K, I> {

  override fun scan(
    workerId: WorkerId,
    pageSize: Int,
    consistentRead: Boolean,
    returnConsumedCapacity: ReturnConsumedCapacity,
    filterExpression: FilterExpression?,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    val scan = DynamoDBScanExpression()
    scan.segment = workerId.segment
    scan.totalSegments = workerId.totalSegments
    scan.isConsistentRead = consistentRead
    scan.limit = pageSize
    scan.withSelect(Select.SPECIFIC_ATTRIBUTES)
    scan.projectionExpression = attributeNames.joinToString(", ")
    scan.withReturnConsumedCapacity(returnConsumedCapacity)
    if (filterExpression != null) {
      scan.filterExpression = filterExpression.expression
      scan.expressionAttributeValues = filterExpression.attributeValues
    }
    if (initialOffset != null) {
      scan.exclusiveStartKey = initialOffset.encodeOffset()
    }
    if (secondaryIndexName != null) {
      scan.indexName = secondaryIndexName
    }
    val page = dynamoDbMapper.scanPage(rawType.java, scan)
    val contents = page.results.map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey?.decodeOffset()
    return Page(
      contents,
      offset,
      page.scannedCount,
      page.consumedCapacity
    )
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
