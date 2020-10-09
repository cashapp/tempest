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

import app.cash.tempest.Codec
import app.cash.tempest.View
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior.CLOBBER
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior.PUT
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression

internal class DynamoDbView<K : Any, I : Any>(
  private val keyCodec: Codec<K, Any>,
  private val itemCodec: Codec<I, Any>,
  private val dynamoDbMapper: DynamoDBMapper
) : View<K, I> {

  override fun load(key: K, consistentReads: DynamoDBMapperConfig.ConsistentReads): I? {
    val keyObject = keyCodec.toDb(key)
    val itemObject = dynamoDbMapper.load(keyObject, consistentReads.config())
    return if (itemObject != null) itemCodec.toApp(itemObject) else null
  }

  override fun save(
    item: I,
    saveExpression: DynamoDBSaveExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val itemObject = itemCodec.toDb(item)
    val saveBehavior = if (ignoreVersionConstraints) CLOBBER else PUT
    dynamoDbMapper.save(itemObject, saveExpression, saveBehavior.config())
  }

  override fun deleteKey(
    key: K,
    deleteExpression: DynamoDBDeleteExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val keyObject = keyCodec.toDb(key)
    deleteInternal(keyObject, deleteExpression, ignoreVersionConstraints)
  }

  override fun delete(
    item: I,
    deleteExpression: DynamoDBDeleteExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val itemObject = itemCodec.toDb(item)
    deleteInternal(itemObject, deleteExpression, ignoreVersionConstraints)
  }

  private fun deleteInternal(
    itemObject: Any,
    deleteExpression: DynamoDBDeleteExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val saveBehavior = if (ignoreVersionConstraints) CLOBBER else PUT
    dynamoDbMapper.delete(itemObject, deleteExpression, saveBehavior.config())
  }
}
