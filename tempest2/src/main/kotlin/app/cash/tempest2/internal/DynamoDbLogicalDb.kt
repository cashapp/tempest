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

import app.cash.tempest.internal.ItemType
import app.cash.tempest.internal.RawItemType
import app.cash.tempest.internal.Schema
import app.cash.tempest2.AsyncLogicalDb
import app.cash.tempest2.AsyncLogicalTable
import app.cash.tempest2.BatchWriteSet
import app.cash.tempest2.ItemSet
import app.cash.tempest2.KeySet
import app.cash.tempest2.LogicalDb
import app.cash.tempest2.LogicalTable
import app.cash.tempest2.ResultWithCapacityConsumed
import app.cash.tempest2.TransactionWriteSet
import app.cash.tempest2.internal.DynamoDbLogicalDb.WriteRequest.Op.CLOBBER
import app.cash.tempest2.internal.DynamoDbLogicalDb.WriteRequest.Op.DELETE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import software.amazon.awssdk.enhanced.dynamodb.Document
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.MappedTableResource
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.internal.EnhancedClientUtils
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult
import software.amazon.awssdk.enhanced.dynamodb.model.ConditionCheck
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch
import software.amazon.awssdk.enhanced.dynamodb.model.TransactGetItemsEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

internal class DynamoDbLogicalDb(
  private val mappedTableResourceFactory: MappedTableResourceFactory,
  private val schema: Schema,
) {

  interface MappedTableResourceFactory {
    fun <T> mappedTableResource(tableName: String, tableSchema: TableSchema<T>): MappedTableResource<T>

    companion object {
      fun simple(create: (String, TableSchema<Any>) -> MappedTableResource<Any>): MappedTableResourceFactory {
        return object : MappedTableResourceFactory {
          override fun <T> mappedTableResource(tableName: String, tableSchema: TableSchema<T>): MappedTableResource<T> {
            return create(tableName, tableSchema as TableSchema<Any>) as MappedTableResource<T>
          }
        }
      }
    }
  }

  fun sync(dynamoDbEnhancedClient: DynamoDbEnhancedClient, logicalTableFactory: LogicalTable.Factory) = Sync(dynamoDbEnhancedClient, logicalTableFactory)

  inner class Sync(
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
    logicalTableFactory: LogicalTable.Factory
  ) : LogicalDb, LogicalTable.Factory by logicalTableFactory {

    override fun batchLoad(
      keys: KeySet,
      consistentReads: Boolean,
      maxPageSize: Int,
    ): ItemSet = executeBatchRequest(keys, consistentReads, maxPageSize).results

    override fun batchLoadWithCapacity(
      keys: KeySet,
      consistentReads: Boolean,
      maxPageSize: Int,
      returnConsumedCapacity: ReturnConsumedCapacity
    ): ResultWithCapacityConsumed<ItemSet> =
      executeBatchRequest(keys, consistentReads, maxPageSize, returnConsumedCapacity)

    private fun executeBatchRequest(
      keys: KeySet,
      consistentReads: Boolean,
      maxPageSize: Int,
      returnConsumedCapacity: ReturnConsumedCapacity? = null
    ): ResultWithCapacityConsumed<ItemSet> {
      val (requestKeys, keysByTable, batchRequests) = toBatchLoadRequests(
        keys,
        consistentReads,
        maxPageSize,
        returnConsumedCapacity
      )

      val pages = batchRequests.map {
        dynamoDbEnhancedClient.batchGetItem(it).iterator().next()
      }

      return toBatchLoadResponse(keysByTable, requestKeys, pages)
    }


    override fun batchWrite(
      writeSet: BatchWriteSet,
      maxPageSize: Int
    ): app.cash.tempest2.BatchWriteResult {
      val (requestsByTable, batchRequests) = toBatchWriteRequests(writeSet, maxPageSize)
      val pages = batchRequests.map {
        dynamoDbEnhancedClient.batchWriteItem(it)
      }
      return toBatchWriteResponse(requestsByTable, pages)
    }

    override fun transactionLoad(keys: KeySet): ItemSet {
      val (requests, batchRequest) = toTransactionLoadRequest(keys)
      val documents = dynamoDbEnhancedClient.transactGetItems(batchRequest)
      return toTransactionLoadResponse(documents, requests)
    }

    override fun transactionWrite(writeSet: TransactionWriteSet) {
      val writeRequest = toTransactionWriteRequest(writeSet)
      try {
        dynamoDbEnhancedClient.transactWriteItems(writeRequest)
      } catch (e: TransactionCanceledException) {
        toTransactionWriteException(writeSet, e)
      }
    }
  }

  fun async(dynamoDbEnhancedClient: DynamoDbEnhancedAsyncClient, logicalTableFactory: AsyncLogicalTable.Factory) = Async(dynamoDbEnhancedClient, logicalTableFactory)

  inner class Async(
    private val dynamoDbEnhancedClient: DynamoDbEnhancedAsyncClient,
    logicalTableFactory: AsyncLogicalTable.Factory
  ) : AsyncLogicalDb, AsyncLogicalTable.Factory by logicalTableFactory {

    override fun batchLoadAsync(
      keys: KeySet,
      consistentReads: Boolean,
      maxPageSize: Int,
    ): Publisher<ItemSet> {
      return executeBatchRequest(
        keys,
        consistentReads,
        maxPageSize,
      )
        .map { it.results }
        .asPublisher()
    }

    override fun batchLoadAsyncWithCapacity(
      keys: KeySet,
      consistentReads: Boolean,
      maxPageSize: Int,
      returnConsumedCapacity: ReturnConsumedCapacity
    ): Publisher<ResultWithCapacityConsumed<ItemSet>> {
      return executeBatchRequest(
        keys,
        consistentReads,
        maxPageSize,
      )
        .asPublisher()
    }

    private fun executeBatchRequest(
      keys: KeySet,
      consistentReads: Boolean,
      maxPageSize: Int,
      returnConsumedCapacity: ReturnConsumedCapacity? = null
    ): Flow<ResultWithCapacityConsumed<ItemSet>> {
      val (requests, requestsByTable, batchRequests) = toBatchLoadRequests(
        keys,
        consistentReads,
        maxPageSize,
        returnConsumedCapacity
      )

      return batchRequests
        .map { request ->
          dynamoDbEnhancedClient.batchGetItem(request).limit(1).asFlow()
        }
        .reduce { acc, item -> merge(acc, item) }
        .map { page -> toBatchLoadResponse(requestsByTable, requests, listOf(page)) }
    }

    override fun batchWriteAsync(
      writeSet: BatchWriteSet,
      maxPageSize: Int
    ): CompletableFuture<app.cash.tempest2.BatchWriteResult> {
      val (requestsByTable, batchRequests) = toBatchWriteRequests(writeSet, maxPageSize)
      val requests = batchRequests.map {
        dynamoDbEnhancedClient.batchWriteItem(it)
          .thenApply { result -> toBatchWriteResponse(requestsByTable, listOf(result)) }
      }
      return CompletableFuture.allOf(*requests.toTypedArray()).thenApply {
        val results = requests.map { it.join() }

        return@thenApply app.cash.tempest2.BatchWriteResult(
          results.flatMap { it.unprocessedClobbers },
          results.flatMap { it.unprocessedDeletes },
        )
      }
    }

    override fun transactionLoadAsync(keys: KeySet): CompletableFuture<ItemSet> {
      val (requests, batchRequest) = toTransactionLoadRequest(keys)
      return dynamoDbEnhancedClient.transactGetItems(batchRequest)
        .thenApply { documents -> toTransactionLoadResponse(documents, requests) }
    }

    override fun transactionWriteAsync(writeSet: TransactionWriteSet): CompletableFuture<Void> {
      val writeRequest = toTransactionWriteRequest(writeSet)
      return dynamoDbEnhancedClient.transactWriteItems(writeRequest)
        .exceptionally { e ->
          // `e` is a java.util.concurrent.CancellationException.
          if (e.cause is TransactionCanceledException) {
            toTransactionWriteException(writeSet, e.cause as TransactionCanceledException) as Void
          } else {
            throw e
          }
        }
    }
  }

  private fun toBatchLoadRequests(
    keys: KeySet,
    consistentReads: Boolean,
    maxPageSize: Int,
    returnConsumedCapacity: ReturnConsumedCapacity?
  ): Triple<List<LoadRequest>, Map<KClass<*>, List<LoadRequest>>, List<BatchGetItemEnhancedRequest>> {
    val requestKeys = keys.map { LoadRequest(it.encodeAsKey().rawItemKey(), it.expectedItemType()) }
    val keysByTable = mutableMapOf<KClass<*>, List<LoadRequest>>()

    val batchRequests = requestKeys.chunked(maxPageSize).map { chunk ->
      val batchByTable = chunk.groupBy { it.tableType }
      keysByTable.putAll(batchByTable)
      BatchGetItemEnhancedRequest.builder()
        .returnConsumedCapacity(returnConsumedCapacity)
        .readBatches(
          batchByTable.map { (tableType, requestsForTable) ->
            ReadBatch.builder(tableType.java)
              .mappedTableResource(mappedTableResource(tableType))
              .apply {
                for (request in requestsForTable) {
                  addGetItem(request.key.key, consistentReads)
                }
              }
              .build()
          }
        )
        .build()
    }
    return Triple(requestKeys, keysByTable, batchRequests)
  }

  private fun toBatchLoadResponse(
    keysByTable: Map<KClass<*>, List<LoadRequest>>,
    requestKeys: List<LoadRequest>,
    pages: List<BatchGetResultPage>
  ): ResultWithCapacityConsumed<ItemSet> {
    val results = mutableSetOf<Any>()
    val consumedCapacity = mutableListOf<ConsumedCapacity>()
    val tableTypes = keysByTable.keys
    val resultTypes = requestKeys.associate { it.key to it.resultType }
    for (page in pages) {
      consumedCapacity.addAll(page.consumedCapacity())
      for (tableType in tableTypes) {
        for (result in page.resultsForTable(mappedTableResource<Any>(tableType))) {
          val resultType = resultTypes[result.rawItemKey()]!!
          val decoded = resultType.codec.toApp(result)
          results.add(decoded)
        }
      }
    }
    return ResultWithCapacityConsumed(ItemSet(results), consumedCapacity)
  }

  private fun toBatchWriteRequests(
    writeSet: BatchWriteSet,
    maxPageSize: Int
  ): Pair<Map<KClass<out Any>, List<WriteRequest>>, List<BatchWriteItemEnhancedRequest>> {
    val clobberRequests = writeSet.itemsToClobber.map { WriteRequest(it.encodeAsItem(), CLOBBER) }
    val deleteRequests = writeSet.keysToDelete.map { WriteRequest(it.encodeAsKey(), DELETE) }
    val requests = clobberRequests + deleteRequests
    val requestsByTable = mutableMapOf<KClass<out Any>, List<WriteRequest>>()

    val batchRequests = requests.chunked(maxPageSize).map { chunk ->
      val batchByTable = chunk.groupBy { it.rawItem::class }
      requestsByTable.putAll(batchByTable)
      BatchWriteItemEnhancedRequest.builder()
        .writeBatches(
          batchByTable.map { (tableType, writeRequestsForTable) ->
            WriteBatch.builder(tableType.java)
              .mappedTableResource(mappedTableResource(tableType))
              .apply {
                for (request in writeRequestsForTable) {
                  when (request.op) {
                    CLOBBER -> addPutItem(request.rawItem)
                    DELETE -> addDeleteItem(request.rawItem)
                  }
                }
              }
              .build()
          }
        )
        .build()
    }

    return Pair(requestsByTable, batchRequests)
  }

  private fun toBatchWriteResponse(
    requestsByTable: Map<KClass<out Any>, List<WriteRequest>>,
    results: List<BatchWriteResult>
  ): app.cash.tempest2.BatchWriteResult {
    val unprocessedClobbers = mutableListOf<Key>()
    val unprocessedDeletes = mutableListOf<Key>()
    val tableTypes = requestsByTable.keys

    for (tableType in tableTypes) {
      val table = mappedTableResource<Any>(tableType)
      val rawClobbersItems = results.flatMap { it.unprocessedPutItemsForTable(table) }
      for (rawItem in rawClobbersItems) {
        unprocessedClobbers.add(rawItem.rawItemKey().key)
      }
      val rawDeleteItems = results.flatMap { it.unprocessedDeleteItemsForTable(table) }
      for (rawItem in rawDeleteItems) {
        unprocessedDeletes.add(rawItem.rawItemKey().key)
      }
    }

    return app.cash.tempest2.BatchWriteResult(
      unprocessedClobbers,
      unprocessedDeletes
    )
  }

  private fun toTransactionLoadRequest(keys: KeySet): Pair<List<LoadRequest>, TransactGetItemsEnhancedRequest> {
    val requests = keys.map { LoadRequest(it.encodeAsKey().rawItemKey(), it.expectedItemType()) }
    val batchRequest = TransactGetItemsEnhancedRequest.builder()
      .apply {
        for (request in requests) {
          addGetItem(mappedTableResource<Any>(request.tableType), request.key.key)
        }
      }
      .build()
    return Pair(requests, batchRequest)
  }

  private fun toTransactionLoadResponse(
    documents: MutableList<Document>,
    requests: List<LoadRequest>
  ): ItemSet {
    val results = mutableSetOf<Any>()
    for ((document, request) in documents.zip(requests)) {
      val result = document.getItem(mappedTableResource(request.tableType)) ?: continue
      val decoded = request.resultType.codec.toApp(result)
      results.add(decoded)
    }
    return ItemSet(results)
  }

  private fun toTransactionWriteRequest(writeSet: TransactionWriteSet): TransactWriteItemsEnhancedRequest? {
    return TransactWriteItemsEnhancedRequest.builder()
      .apply {
        for (itemToSave in writeSet.itemsToSave) {
          addUpdateItem(itemToSave.encodeAsItem(), writeSet.writeExpressions[itemToSave])
        }
        for (keyToDelete in writeSet.keysToDelete) {
          addDeleteItem(keyToDelete.encodeAsKey(), writeSet.writeExpressions[keyToDelete])
        }
        for (keyToCheck in writeSet.keysToCheck) {
          addConditionCheck(keyToCheck.encodeAsKey(), writeSet.writeExpressions[keyToCheck])
        }
        if (writeSet.idempotencyToken != null) {
          clientRequestToken(writeSet.idempotencyToken)
        }
      }
      .build()
  }

  fun toTransactionWriteException(writeSet: TransactionWriteSet, e: TransactionCanceledException) {
    // We don't want to wrap these exceptions but only add a more useful message so upstream callers can themselves
    // parse the potentially concurrency related TransactionCancelledExceptions
    // https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/model/TransactionCanceledException.html
    throw TransactionCanceledException.builder()
      .message(
        "Write transaction failed: ${writeSet.describeOperations()}.\n" +
          " Aws error message: ${e.message}"
      )
      .cancellationReasons(e.cancellationReasons())
      .build()
  }

  private fun Any.rawItemKey(): RawItemKey {
    val rawItemType = expectedRawItemType()
    return RawItemKey(
      rawItemType.tableName,
      EnhancedClientUtils.createKeyFromItem(
        this,
        mappedTableResource<Any>(this::class).tableSchema(),
        TableMetadata.primaryIndexName(),
      ),
      rawItemType.hashKeyName,
      rawItemType.rangeKeyName,
    )
  }

  private fun Any.expectedRawItemType(): RawItemType {
    return requireNotNull(
      schema.resolveEnclosingRawItemType(
        this::class
      )
    ) { "Cannot find a dynamodb table for ${this::class}" }
  }

  private fun <T : Any> mappedTableResource(tableType: KClass<*>): MappedTableResource<T> {
    val rawItemType = schema.getRawItem(tableType)!!
    return mappedTableResourceFactory.mappedTableResource(
      rawItemType.tableName,
      TableSchemaFactory.create(rawItemType.type.java)
    )
  }

  private fun Any.expectedItemType(): ItemType {
    return requireNotNull(
      schema.resolveEnclosingItemType(
        this::class
      )
    ) { "Cannot find an item type for ${this::class}" }
  }

  private fun Any.encodeAsKey(): Any {
    val type = this::class
    // Fallback to item codec because keys can be extracted from items.
    val codec = schema.getKey(type)?.codec ?: schema.getItem(type)?.codec
    requireNotNull(codec) { "Failed to encode $type" }
    return codec.toDb(this)
  }

  private fun Any.encodeAsItem(): Any {
    val type = this::class
    val codec = schema.getItem(type)?.codec
    requireNotNull(codec) { "Failed to encode $type" }
    return codec.toDb(this)
  }

  private fun TransactionWriteSet.describeOperations(): List<String> {
    val descriptions = mutableListOf<String>()
    for (itemToSave in itemsToSave) {
      val rawItemKey = itemToSave.encodeAsItem().rawItemKey()
      descriptions.add("Save item (non-key attributes omitted) $rawItemKey")
    }
    for (keyToDelete in keysToDelete) {
      val rawItemKey = keyToDelete.encodeAsKey().rawItemKey()
      descriptions.add("Delete key $rawItemKey")
    }
    for (keyToCheck in keysToCheck) {
      val rawItemKey = keyToCheck.encodeAsKey().rawItemKey()
      descriptions.add("Check key $rawItemKey")
    }
    return descriptions.toList()
  }

  private fun <T> ReadBatch.Builder<T>.addGetItem(key: Key, consistentReads: Boolean) =
    addGetItem(
      GetItemEnhancedRequest.builder()
        .key(key)
        .consistentRead(consistentReads)
        .build()
    )

  private fun <T : Any> TransactWriteItemsEnhancedRequest.Builder.addUpdateItem(
    item: T,
    expression: Expression?
  ) = addUpdateItem(
    mappedTableResource<T>(item::class),
    UpdateItemEnhancedRequest.builder(item.javaClass)
      .item(item)
      .conditionExpression(expression)
      .build()
  )

  private fun <T : Any> TransactWriteItemsEnhancedRequest.Builder.addDeleteItem(
    item: T,
    expression: Expression?
  ) = addDeleteItem(
    mappedTableResource<T>(item::class),
    DeleteItemEnhancedRequest.builder()
      .key(item.rawItemKey().key)
      .conditionExpression(expression)
      .build()
  )

  private fun <T : Any> TransactWriteItemsEnhancedRequest.Builder.addConditionCheck(
    item: T,
    expression: Expression?
  ) = addConditionCheck(
    mappedTableResource<T>(item::class),
    ConditionCheck.builder()
      .key(item.rawItemKey().key)
      .conditionExpression(expression)
      .build()
  )

  private data class RawItemKey(
    val tableName: String,
    val key: Key,
    val hashKeyName: String,
    val rangeKeyName: String?,
  ) {
    val hashKeyValue: AttributeValue get() = key.partitionKeyValue()
    val rangeKeyValue: AttributeValue? get() = key.sortKeyValue().orElse(null)

    override fun toString() = "$tableName[$hashKeyName=$hashKeyValue,$rangeKeyName=$rangeKeyValue]"
  }

  private data class LoadRequest(
    val key: RawItemKey,
    val resultType: ItemType
  ) {
    val tableType = resultType.rawItemType
  }

  private data class WriteRequest(
    val rawItem: Any,
    val op: Op
  ) {
    enum class Op {
      CLOBBER,
      DELETE
    }
  }
}
