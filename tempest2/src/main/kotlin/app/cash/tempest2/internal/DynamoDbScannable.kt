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

package app.cash.tempest2.internal

import app.cash.tempest.internal.Codec
import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import app.cash.tempest2.Scannable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal class DynamoDbScannable<K : Any, I : Any, R : Any>(
  private val secondaryIndexName: String?,
  private val attributeNames: Set<String>,
  private val keyCodec: Codec<K, R>,
  private val itemCodec: Codec<I, R>,
  private val dynamoDbTable: DynamoDbTable<R>
) : Scannable<K, I> {

  override fun scan(
    pageSize: Int,
    consistentRead: Boolean,
    filterExpression: Expression?,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    val scan = ScanEnhancedRequest.builder()
      .consistentRead(consistentRead)
      .limit(pageSize)
      .attributesToProject(attributeNames)
    if (filterExpression != null) {
      scan.filterExpression(filterExpression)
    }
    if (initialOffset != null) {
      scan.exclusiveStartKey(initialOffset.encodeOffset())
    }
    val page = if (secondaryIndexName != null) {
      dynamoDbTable.index(secondaryIndexName).scan(scan.build())
    } else {
      dynamoDbTable.scan(scan.build())
    }
      .iterator().next()

    val contents = page.items().map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey()?.decodeOffset()
    return Page(contents, offset)
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
}
