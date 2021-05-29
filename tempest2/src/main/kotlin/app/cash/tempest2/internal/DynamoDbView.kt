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
import app.cash.tempest2.AsyncView
import app.cash.tempest2.View
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.internal.EnhancedClientUtils
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest
import java.util.concurrent.CompletableFuture

internal class DynamoDbView<K : Any, I : Any, R : Any>(
  private val keyCodec: Codec<K, R>,
  private val itemCodec: Codec<I, R>,
  private val tableSchema: TableSchema<R>,
) {

  fun sync(dynamoDbTable: DynamoDbTable<R>) = Sync(dynamoDbTable)

  inner class Sync(
    private val dynamoDbTable: DynamoDbTable<R>
  ) : View<K, I> {
    override fun load(key: K, consistentReads: Boolean): I? {
      val request = toLoadRequest(key, consistentReads)
      val itemObject = dynamoDbTable.getItem(request)
      return toLoadResponse(itemObject)
    }

    override fun save(
      item: I,
      saveExpression: Expression?
    ) {
      val request = toSaveRequest(item, saveExpression)
      dynamoDbTable.putItem(request)
    }

    override fun deleteKey(
      key: K,
      deleteExpression: Expression?
    ): I? {
      val request = toDeleteKeyRequest(key, deleteExpression)
      val itemObject = dynamoDbTable.deleteItem(request)
      return toDeleteResponse(itemObject)
    }

    override fun delete(
      item: I,
      deleteExpression: Expression?
    ): I? {
      val request = toDeleteItemRequest(item, deleteExpression)
      val itemObject = dynamoDbTable.deleteItem(request)
      return toDeleteResponse(itemObject)
    }
  }

  fun async(dynamoDbTable: DynamoDbAsyncTable<R>) = Async(dynamoDbTable)

  inner class Async(
    private val dynamoDbTable: DynamoDbAsyncTable<R>
  ) : AsyncView<K, I> {
    override fun loadAsync(key: K, consistentReads: Boolean): CompletableFuture<I?> {
      val request = toLoadRequest(key, consistentReads)
      return dynamoDbTable.getItem(request).thenApply(::toDeleteResponse)
    }

    override fun saveAsync(
      item: I,
      saveExpression: Expression?
    ): CompletableFuture<Void> {
      val request = toSaveRequest(item, saveExpression)
      return dynamoDbTable.putItem(request)
    }

    override fun deleteKeyAsync(
      key: K,
      deleteExpression: Expression?
    ): CompletableFuture<I?> {
      val request = toDeleteKeyRequest(key, deleteExpression)
      return dynamoDbTable.deleteItem(request).thenApply(::toDeleteResponse)
    }

    override fun deleteAsync(
      item: I,
      deleteExpression: Expression?
    ): CompletableFuture<I?> {
      val request = toDeleteItemRequest(item, deleteExpression)
      return dynamoDbTable.deleteItem(request).thenApply(::toDeleteResponse)
    }
  }

  private fun R.key(): Key {
    return EnhancedClientUtils.createKeyFromItem(
      this, tableSchema,
      TableMetadata.primaryIndexName()
    )
  }

  private fun toLoadRequest(key: K, consistentReads: Boolean): GetItemEnhancedRequest {
    val keyObject = keyCodec.toDb(key)
    return GetItemEnhancedRequest.builder()
      .key(keyObject.key())
      .consistentRead(consistentReads)
      .build()
  }

  private fun toLoadResponse(itemObject: R?) = if (itemObject != null) itemCodec.toApp(itemObject) else null

  private fun toSaveRequest(item: I, saveExpression: Expression?): PutItemEnhancedRequest<R> {
    val itemObject = itemCodec.toDb(item)
    return PutItemEnhancedRequest.builder(tableSchema.itemType().rawClass())
      .item(itemObject)
      .conditionExpression(saveExpression)
      .build()
  }

  private fun toDeleteKeyRequest(key: K, deleteExpression: Expression?): DeleteItemEnhancedRequest {
    val keyObject = keyCodec.toDb(key)
    return DeleteItemEnhancedRequest.builder()
      .key(keyObject.key())
      .conditionExpression(deleteExpression)
      .build()
  }

  private fun toDeleteItemRequest(item: I, deleteExpression: Expression?): DeleteItemEnhancedRequest {
    val itemObject = itemCodec.toDb(item)
    return DeleteItemEnhancedRequest.builder()
      .key(itemObject.key())
      .conditionExpression(deleteExpression)
      .build()
  }

  private fun toDeleteResponse(itemObject: R?) = if (itemObject != null) itemCodec.toApp(itemObject) else null
}
