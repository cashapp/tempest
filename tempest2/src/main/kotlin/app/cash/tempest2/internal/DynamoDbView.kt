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
import app.cash.tempest2.View
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata
import software.amazon.awssdk.enhanced.dynamodb.internal.EnhancedClientUtils
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest

internal class DynamoDbView<K : Any, I : Any, R : Any>(
  private val keyCodec: Codec<K, R>,
  private val itemCodec: Codec<I, R>,
  private val dynamoDbTable: DynamoDbTable<R>
) : View<K, I> {

  override fun load(key: K, consistentReads: Boolean): I? {
    val keyObject = keyCodec.toDb(key)
    val request = GetItemEnhancedRequest.builder()
      .key(keyObject.key())
      .consistentRead(consistentReads)
      .build()
    val itemObject = dynamoDbTable.getItem(request)
    return if (itemObject != null) itemCodec.toApp(itemObject) else null
  }

  override fun save(
    item: I,
    saveExpression: Expression?
  ) {
    val itemObject = itemCodec.toDb(item)
    val request = PutItemEnhancedRequest.builder(dynamoDbTable.tableSchema().itemType().rawClass())
      .item(itemObject)
      .conditionExpression(saveExpression)
      .build()
    dynamoDbTable.putItem(request)
  }

  override fun deleteKey(
    key: K,
    deleteExpression: Expression?
  ) {
    val keyObject = keyCodec.toDb(key)
    deleteInternal(keyObject, deleteExpression)
  }

  override fun delete(
    item: I,
    deleteExpression: Expression?
  ) {
    val itemObject = itemCodec.toDb(item)
    deleteInternal(itemObject, deleteExpression)
  }

  private fun deleteInternal(
    itemObject: R,
    deleteExpression: Expression?
  ) {
    val request = DeleteItemEnhancedRequest.builder()
      .key(itemObject.key())
      .conditionExpression(deleteExpression)
      .build()
    dynamoDbTable.deleteItem(request)
  }

  private fun R.key(): Key {
    return EnhancedClientUtils.createKeyFromItem(
      this, dynamoDbTable.tableSchema(),
      TableMetadata.primaryIndexName()
    )
  }
}
