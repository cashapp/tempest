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

package app.cash.tempest2

import app.cash.tempest2.extensions.WithResultExtension
import app.cash.tempest2.extensions.WithResultExtension.Companion.WithResultExtensionInstalledLast
import kotlinx.coroutines.future.await
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.extensions.VersionedRecordExtension
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity
import java.util.concurrent.CompletableFuture

interface AsyncView<K : Any, I : Any> {
  /**
   * Returns an item whose keys match those of the prototype key object given, or null if no
   * such item exists.
   */
  suspend fun load(key: K, consistentReads: Boolean = false): I? = loadAsync(key, consistentReads).await()

  suspend fun load(
    key: K,
    consistentReads: Boolean = false,
    returnConsumedCapacity: ReturnConsumedCapacity = ReturnConsumedCapacity.TOTAL
  ): Pair<I?, ConsumedCapacity?> = loadAsync(key, consistentReads, returnConsumedCapacity).await()

  /**
   * Saves an item in DynamoDB. This method uses [DynamoDbClient.putItem] to clear
   * and replace all attributes, including unmodeled ones, on save. Partial update, i.e.
   * [DynamoDbClient.updateItem], is not supported yet.
   *
   * Any options specified in the [saveExpression] parameter will be overlaid on any constraints due
   * to versioned attributes.
   */
  suspend fun save(
    item: I,
    saveExpression: Expression? = null
  ) = saveAsync(item, saveExpression).await()

  /**
   * This method requires the [WithResultExtension] to be installed on the [DynamoDbEnhancedClient].
   * This extension must be installed last!
   *
   * Saves an item in DynamoDB. This method uses [DynamoDbClient.putItem] to clear
   * and replace all attributes, including unmodeled ones, on save. Partial update, i.e.
   * [DynamoDbClient.updateItem], is not supported yet.
   *
   * Any options specified in the [saveExpression] parameter will be overlaid on any constraints due
   * to versioned attributes.
   *
   * Any auto generated in memory updates will be reflected in the result item.
   */
  @WithResultExtensionInstalledLast
  suspend fun saveWithResult(
    item: I,
    saveExpression: Expression? = null
  ) = saveAsyncWithResult(item, saveExpression).await()


  /**
   * Deletes the item identified by [key] from its DynamoDB table using [deleteExpression]. Any
   * options specified in the [deleteExpression] parameter will be overlaid on any constraints due
   * to versioned attributes.
   *
   * If the item to be deleted has versioned attributes, load the item and use [delete] instead.
   * For more information, see [VersionedRecordExtension].
   */
  suspend fun deleteKey(
    key: K,
    deleteExpression: Expression? = null
  ) = deleteKeyAsync(key, deleteExpression).await()

  /**
   * Deletes [item] from its DynamoDB table using [deleteExpression]. Any options specified in the
   * [deleteExpression] parameter will be overlaid on any constraints due to versioned attributes.
   */
  suspend fun delete(
    item: I,
    deleteExpression: Expression? = null
  ) = deleteAsync(item, deleteExpression).await()

  /**
   * Performs an upsert operation using DynamoDB's UpdateItem operation.
   * Creates or updates an item with the provided data and optional condition.
   *
   * This operation uses a single UpdateItem call. Any exceptions from
   * DynamoDB (including ConditionalCheckFailedException) are bubbled up
   * to the caller.
   *
   * @param item The item to upsert
   * @param upsertExpression Optional condition expression for the upsert
   * @return The result of the UpdateItem operation
   */
  suspend fun upsert(item: I, upsertExpression: Expression? = null): I? = upsertAsync(item, upsertExpression).await()

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun loadAsync(key: K, consistentReads: Boolean): CompletableFuture<I?>

  fun loadAsync(
    key: K,
    consistentReads: Boolean,
    returnConsumedCapacity: ReturnConsumedCapacity
  ): CompletableFuture<Pair<I?, ConsumedCapacity?>>

  fun loadAsync(key: K) = loadAsync(key, false)

  fun saveAsync(
    item: I,
    saveExpression: Expression?
  ): CompletableFuture<Void>

  @WithResultExtensionInstalledLast
  fun saveAsyncWithResult(
    item: I,
    saveExpression: Expression?
  ): CompletableFuture<I>

  fun saveAsync(
    item: I
  ) = saveAsync(item, saveExpression = null)

  @WithResultExtensionInstalledLast
  fun saveAsyncWithResult(
    item: I
  ) = saveAsyncWithResult(item, saveExpression = null)

  fun deleteKeyAsync(
    key: K,
    deleteExpression: Expression?
  ): CompletableFuture<I?>

  fun deleteKeyAsync(
    key: K
  ) = deleteKeyAsync(key, deleteExpression = null)

  fun deleteAsync(
    item: I,
    deleteExpression: Expression?
  ): CompletableFuture<I?>

  fun deleteAsync(
    item: I
  ) = deleteAsync(item, deleteExpression = null)

  fun upsertAsync(
    item: I,
    upsertExpression: Expression? = null): CompletableFuture<I?>

  fun upsertAsync(
    item: I
  ) = upsertAsync(item, upsertExpression = null)
}
