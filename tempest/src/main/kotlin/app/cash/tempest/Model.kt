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

package app.cash.tempest

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTransactionWriteExpression
import kotlin.reflect.KClass

/**
 * A write that the client sends to the DynamoDb service.
 */
data class BatchWriteSet(
  val itemsToClobber: ItemSet,
  val keysToDelete: KeySet
) {

  class Builder {
    private val itemsToClobber = mutableSetOf<Any>()
    private val keysToDelete = mutableSetOf<Any>()

    /**
     * This method behaves as if SaveBehavior.CLOBBER was specified. Versioned attributes will be
     * discarded.
     */
    fun clobber(
      vararg item: Any
    ) = apply {
      itemsToClobber.addAll(item.toSet())
    }

    fun clobber(
      items: Iterable<Any>
    ) = apply {
      itemsToClobber.addAll(items)
    }

    fun delete(
      vararg key: Any
    ) = apply {
      keysToDelete.addAll(key.toSet())
    }

    fun delete(
      keys: Iterable<Any>
    ) = apply {
      keysToDelete.addAll(keys)
    }

    fun build(): BatchWriteSet {
      return BatchWriteSet(
          ItemSet(
              itemsToClobber
          ), KeySet(keysToDelete)
      )
    }
  }
}

/**
 * It contains the information about the unprocessed items and the
 * exception causing the failure.
 */
data class BatchWriteResult(
  val failedBatches: List<DynamoDBMapper.FailedBatch>
) {
  val isSuccessful = failedBatches.isEmpty()
}

data class TransactionWriteSet(
  val itemsToSave: ItemSet,
  val keysToDelete: KeySet,
  val keysToCheck: KeySet,
  val writeExpressions: Map<Any, DynamoDBTransactionWriteExpression>,
  val idempotencyToken: String?
) {

  val size
    get() = itemsToSave.size + keysToDelete.size + keysToCheck.size

  class Builder {
    private val itemsToSave = mutableSetOf<Any>()
    private val keysToDelete = mutableSetOf<Any>()
    private val keysToCheck = mutableSetOf<Any>()
    private val writeExpressions = mutableMapOf<Any, DynamoDBTransactionWriteExpression>()
    private var idempotencyToken: String? = null

    val size
      get() = itemsToSave.size + keysToDelete.size + keysToCheck.size

    /**
     * This adds a put operation to clear and replace all attributes, including unmodeled ones.
     * Partial update is not supported.
     */
    @JvmOverloads
    fun save(
      item: Any,
      expression: DynamoDBTransactionWriteExpression? = null
    ) = apply {
      require(!itemsToSave.contains(item) && !itemsToSave.contains(item)) {
        "Duplicate items are not allowed: $item." }
      itemsToSave.add(item)
      if (expression != null) {
        writeExpressions[item] = expression
      }
    }

    @JvmOverloads
    fun delete(
      key: Any,
      expression: DynamoDBTransactionWriteExpression? = null
    ) = apply {
      require(!keysToDelete.contains(key) && !keysToDelete.contains(key)) {
        "Duplicate items are not allowed: $key." }
      keysToDelete.add(key)
      if (expression != null) {
        writeExpressions[key] = expression
      }
    }

    @JvmOverloads
    fun checkCondition(
      key: Any,
      expression: DynamoDBTransactionWriteExpression? = null
    ) = apply {
      require(!keysToCheck.contains(key) && !keysToCheck.contains(key)) {
        "Duplicate items are not allowed: $key." }
      keysToCheck.add(key)
      if (expression != null) {
        writeExpressions[key] = expression
      }
    }

    fun idempotencyToken(idempotencyToken: String) = apply {
      this.idempotencyToken = idempotencyToken
    }

    fun addAll(builder: Builder) {
      check(builder.idempotencyToken == null) { "too many idempotency tokens" }

      for (item in builder.itemsToSave) {
        save(item)
      }
      for (item in builder.keysToDelete) {
        delete(item)
      }
      for (item in builder.keysToCheck) {
        checkCondition(item)
      }

      writeExpressions += builder.writeExpressions
    }

    fun build(): TransactionWriteSet {
      return TransactionWriteSet(
          ItemSet(itemsToSave),
          KeySet(keysToDelete),
          KeySet(keysToCheck),
          writeExpressions.toMap(),
          idempotencyToken
      )
    }
  }
}

/**
 * A collection of keys or items across tables.
 */
class KeySet private constructor(
  private val contents: Set<Any>
) : Set<Any> by contents {

  constructor(contents: Iterable<Any>) : this(contents.toSet())

  fun <K : Any> getKeys(
    keyType: KClass<K>
  ): List<K> = getKeys(keyType.java)

  fun <K : Any> getKeys(
    keyType: Class<K>
  ): List<K> {
    return contents.filterIsInstance(keyType)
  }

  inline fun <reified K : Any> getKeys(): List<K> {
    return getKeys(K::class)
  }
}

/**
 * A collection of items across tables.
 */
class ItemSet private constructor(
  private val contents: Set<Any>
) : Set<Any> by contents {

  constructor(contents: Iterable<Any>) : this(contents.toSet())

  fun <I : Any> getItems(
    itemType: KClass<I>
  ): List<I> = getItems(itemType.java)

  fun <I : Any> getItems(
    itemType: Class<I>
  ): List<I> {
    return contents.filterIsInstance(itemType)
  }

  inline fun <reified I : Any> getItems(): List<I> {
    return getItems(I::class)
  }
}
