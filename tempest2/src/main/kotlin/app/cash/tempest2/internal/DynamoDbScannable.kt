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
import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import app.cash.tempest2.Scannable
import kotlinx.coroutines.reactive.awaitFirst
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

internal class DynamoDbScannable<K : Any, I : Any, R : Any>(
  private val secondaryIndexName: String?,
  private val attributeNames: Set<String>,
  private val keyCodec: Codec<K, R>,
  private val itemCodec: Codec<I, R>,
  private val tableSchema: TableSchema<R>,
) {

  fun sync(dynamoDbTable: DynamoDbTable<R>) = Sync(dynamoDbTable)

  inner class Sync(
    private val dynamoDbTable: DynamoDbTable<R>
  ) : Scannable<K, I> {

    override fun scan(
      pageSize: Int,
      consistentRead: Boolean,
      filterExpression: Expression?,
      initialOffset: Offset<K>?
    ): Page<K, I> {
      val request = toScanRequest(consistentRead, pageSize, filterExpression, initialOffset)
      val page = if (secondaryIndexName != null) {
        dynamoDbTable.index(secondaryIndexName).scan(request)
      } else {
        dynamoDbTable.scan(request)
      }
        .iterator().next()
      return toScanResponse(page)
    }
  }

  fun async(dynamoDbTable: DynamoDbAsyncTable<R>) = Async(dynamoDbTable)

  inner class Async(
    private val dynamoDbTable: DynamoDbAsyncTable<R>
  ) : app.cash.tempest2.async.Scannable<K, I> {

    override suspend fun scan(
      pageSize: Int,
      consistentRead: Boolean,
      filterExpression: Expression?,
      initialOffset: Offset<K>?
    ): Page<K, I> {
      val request = toScanRequest(consistentRead, pageSize, filterExpression, initialOffset)
      val page = if (secondaryIndexName != null) {
        dynamoDbTable.index(secondaryIndexName).scan(request)
      } else {
        dynamoDbTable.scan(request)
      }
        .limit(1).awaitFirst()
      return toScanResponse(page)
    }
  }

  private fun toScanRequest(
    consistentRead: Boolean,
    pageSize: Int,
    filterExpression: Expression?,
    initialOffset: Offset<K>?
  ): ScanEnhancedRequest {
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
    return scan.build()
  }

  private fun toScanResponse(page: software.amazon.awssdk.enhanced.dynamodb.model.Page<R>): Page<K, I> {
    val contents = page.items().map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey()?.decodeOffset()
    return Page(contents, offset)
  }

  private fun Offset<K>.encodeOffset(): Map<String, AttributeValue> {
    val offsetKey = keyCodec.toDb(key)
    return tableSchema.itemToMap(offsetKey, true)
  }

  private fun Map<String, AttributeValue>.decodeOffset(): Offset<K> {
    val offsetKeyAttributes = tableSchema.mapToItem(this)
    val offsetKey = keyCodec.toApp(offsetKeyAttributes)
    return Offset(offsetKey)
  }
}
