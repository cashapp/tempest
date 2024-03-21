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

import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.extensions.VersionedRecordExtension
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity

interface View<K : Any, I : Any> {
  /**
   * Returns an item whose keys match those of the prototype key object given, or null if no
   * such item exists.
   */
  fun load(key: K, consistentReads: Boolean = false): I?

  fun load(
    key: K,
    consistentReads: Boolean = false,
    returnConsumedCapacity: ReturnConsumedCapacity = ReturnConsumedCapacity.TOTAL
  ): Pair<I?, ConsumedCapacity?>

  /**
   * Saves an item in DynamoDB. This method uses [DynamoDbClient.putItem] to clear
   * and replace all attributes, including unmodeled ones, on save. Partial update, i.e.
   * [DynamoDbClient.updateItem], is not supported yet.
   *
   * Any options specified in the [saveExpression] parameter will be overlaid on any constraints due
   * to versioned attributes.
   */
  fun save(
    item: I,
    saveExpression: Expression? = null
  )

  /**
   * Deletes the item identified by [key] from its DynamoDB table using [deleteExpression]. Any
   * options specified in the [deleteExpression] parameter will be overlaid on any constraints due
   * to versioned attributes.
   *
   * If the item to be deleted has versioned attributes, load the item and use [delete] instead.
   * For more information, see [VersionedRecordExtension].
   */
  fun deleteKey(
    key: K,
    deleteExpression: Expression? = null
  ): I?

  /**
   * Deletes [item] from its DynamoDB table using [deleteExpression]. Any options specified in the
   * [deleteExpression] parameter will be overlaid on any constraints due to versioned attributes.
   */
  fun delete(
    item: I,
    deleteExpression: Expression? = null
  ): I?

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun load(key: K) = load(key, false)

  fun save(
    item: I
  ) = save(item, saveExpression = null)

  fun deleteKey(
    key: K
  ) = deleteKey(key, deleteExpression = null)

  fun delete(
    item: I
  ) = delete(item, deleteExpression = null)
}
