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
import app.cash.tempest2.AsyncScannable
import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import app.cash.tempest2.Scannable
import app.cash.tempest2.WorkerId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
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
      initialOffset: Offset<K>?,
      workerId: WorkerId?
    ): Page<K, I> {
      val request = toScanRequest(consistentRead, pageSize, filterExpression, initialOffset, workerId)
      val page = if (secondaryIndexName != null) {
        dynamoDbTable.index(secondaryIndexName).scan(request)
      } else {
        dynamoDbTable.scan(request)
      }
        .iterator().next()
      return toScanResponse(page)
    }

    override fun scanAll(
      pageSize: Int,
      consistentRead: Boolean,
      filterExpression: Expression?,
      initialOffset: Offset<K>?,
      workerId: WorkerId?
    ): Sequence<Page<K, I>> {
      return generateSequence(
        scan(pageSize, consistentRead, filterExpression, initialOffset, workerId)
      ) { page ->
        page.offset?.let { offset ->
          scan(pageSize, consistentRead, filterExpression, offset, workerId)
        }
      }
    }

    override fun scanAllContents(
      pageSize: Int,
      consistentRead: Boolean,
      filterExpression: Expression?,
      initialOffset: Offset<K>?,
      workerId: WorkerId?
    ): Sequence<I> {
      return scanAll(pageSize, consistentRead, filterExpression, initialOffset, workerId)
        .map { it.contents }
        .flatten()
    }
  }

  fun async(dynamoDbTable: DynamoDbAsyncTable<R>) = Async(dynamoDbTable)

  inner class Async(
    private val dynamoDbTable: DynamoDbAsyncTable<R>
  ) : AsyncScannable<K, I> {

    override fun scanAsync(
      pageSize: Int,
      consistentRead: Boolean,
      filterExpression: Expression?,
      initialOffset: Offset<K>?,
    ): Publisher<Page<K, I>> {
      val request = toScanRequest(consistentRead, pageSize, filterExpression, initialOffset)
      return if (secondaryIndexName != null) {
        dynamoDbTable.index(secondaryIndexName).scan(request)
      } else {
        dynamoDbTable.scan(request)
      }
        .limit(1)
        .asFlow()
        .map(::toScanResponse)
        .asPublisher()
    }
  }

  private fun toScanRequest(
    consistentRead: Boolean,
    pageSize: Int,
    filterExpression: Expression?,
    initialOffset: Offset<K>?,
    workerId: WorkerId? = null
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
    if (workerId != null) {
      scan.segment(workerId.segment)
      scan.totalSegments(workerId.totalSegments)
    }
    return scan.build()
  }

  private fun toScanResponse(page: software.amazon.awssdk.enhanced.dynamodb.model.Page<R>): Page<K, I> {
    val contents = page.items().map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey()?.decodeOffset()
    return Page(contents, offset, page.consumedCapacity())
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
