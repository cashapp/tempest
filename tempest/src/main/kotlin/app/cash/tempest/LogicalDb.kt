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

package app.cash.tempest

import app.cash.tempest.internal.LogicalDbFactory
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.BatchLoadRetryStrategy
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.BatchWriteRetryStrategy
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads.EVENTUAL
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.DefaultBatchLoadRetryStrategy
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.DefaultBatchWriteRetryStrategy
import javax.annotation.CheckReturnValue
import kotlin.reflect.KClass

/**
 * A collection of tables that implement the DynamoDB best practice of putting multiple
 * item types into the same storage table. This makes it possible to perform aggregate operations
 * and transactions on those item types.
 */
interface LogicalDb : LogicalTable.Factory {

  /**
   * Retrieves multiple items from multiple tables using their primary keys.
   *
   * This method performs one or more calls to the [AmazonDynamoDB.batchGetItem] API.
   *
   * A single operation can retrieve up to 16 MB of data, which can contain as many as 100 items.
   * BatchGetItem returns a partial result if the response size limit is exceeded, the table's
   * provisioned throughput is exceeded, or an internal processing failure occurs. If a partial
   * result is returned, this method backs off and retries the `UnprocessedKeys` in the next API
   * call.
   */
  fun batchLoad(
    keys: KeySet,
    consistentReads: ConsistentReads = EVENTUAL,
    retryStrategy: BatchLoadRetryStrategy = DefaultBatchLoadRetryStrategy()
  ): ItemSet

  fun batchLoad(
    keys: Iterable<Any>,
    consistentReads: ConsistentReads = EVENTUAL,
    retryStrategy: BatchLoadRetryStrategy = DefaultBatchLoadRetryStrategy()
  ): ItemSet {
    return batchLoad(KeySet(keys), consistentReads)
  }

  fun batchLoad(
    vararg keys: Any,
    consistentReads: ConsistentReads = EVENTUAL,
    retryStrategy: BatchLoadRetryStrategy = DefaultBatchLoadRetryStrategy()
  ): ItemSet {
    return batchLoad(keys.toList(), consistentReads)
  }

  /**
   * Saves and deletes the objects given using one or more calls to the
   * [AmazonDynamoDB.batchWriteItem] API. **Callers should always check the returned
   * [BatchWriteResult]** because this method returns normally even if some writes were not
   * performed.
   *
   * This method does not support versioning annotations and behaves as if
   * [DynamoDBMapperConfig.SaveBehavior.CLOBBER] was specified.
   *
   * A single call to BatchWriteItem can write up to 16 MB of data, which can comprise as many as 25
   * put or delete requests. Individual items to be written can be as large as 400 KB.
   *
   * In order to improve performance with these large-scale operations, this does not behave
   * in the same way as individual PutItem and DeleteItem calls would. For example, you cannot specify
   * conditions on individual put and delete requests, and BatchWriteItem does not return deleted
   * items in the response.
   */
  @CheckReturnValue
  fun batchWrite(
    writeSet: BatchWriteSet,
    retryStrategy: BatchWriteRetryStrategy = DefaultBatchWriteRetryStrategy()
  ): BatchWriteResult

  /**
   * Transactionally loads objects specified by transactionLoadRequest by calling
   * [AmazonDynamoDB.transactGetItems] API.
   *
   * A transaction cannot contain more than 25 unique items.
   * A transaction cannot contain more than 4 MB of data.
   * No two actions in a transaction can work against the same item in the same table.
   */
  fun transactionLoad(keys: KeySet): ItemSet

  fun transactionLoad(keys: Iterable<Any>): ItemSet {
    return transactionLoad(KeySet(keys))
  }

  fun transactionLoad(vararg keys: Any): ItemSet {
    return transactionLoad(keys.toList())
  }

  /**
   * Transactionally writes objects specified by transactionWriteRequest by calling
   * [AmazonDynamoDB.transactWriteItems] API.
   *
   * This method supports versioning annotations, but not in conjunction with condition expressions.
   * It throws [com.amazonaws.SdkClientException] exception if class of any input object is annotated
   * with [DynamoDBVersionAttribute] or [DynamoDBVersioned] and a condition expression is also present.
   *
   * A transaction cannot contain more than 25 unique items, including conditions.
   * A transaction cannot contain more than 4 MB of data.
   * No two actions in a transaction can work against the same item in the same table.
   * For example, you cannot both ConditionCheck and Update the same item in one transaction.
   */
  fun transactionWrite(writeSet: TransactionWriteSet)

  companion object {
    inline operator fun <reified DB : LogicalDb> invoke(
      dynamoDbMapper: DynamoDBMapper,
      config: DynamoDBMapperConfig = DynamoDBMapperConfig.DEFAULT
    ): DB {
      return create(DB::class, dynamoDbMapper, config)
    }

    fun <DB : LogicalDb> create(
      dbType: KClass<DB>,
      dynamoDbMapper: DynamoDBMapper,
      config: DynamoDBMapperConfig = DynamoDBMapperConfig.DEFAULT
    ): DB {
      return LogicalDbFactory(dynamoDbMapper, config).logicalDb(dbType)
    }

    // Overloaded functions for Java callers (Kotlin interface companion objects do not support
    // having @JvmStatic and `@JvmOverloads` at the same time).
    // https://youtrack.jetbrains.com/issue/KT-35716

    @JvmStatic
    fun <DB : LogicalDb> create(
      dbType: Class<DB>,
      dynamoDbMapper: DynamoDBMapper
    ) = create(dbType.kotlin, dynamoDbMapper)

    @JvmStatic
    fun <DB : LogicalDb> create(
      dbType: Class<DB>,
      dynamoDbMapper: DynamoDBMapper,
      config: DynamoDBMapperConfig
    ) = create(dbType.kotlin, dynamoDbMapper, config)
  }

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun batchLoad(
    keys: Iterable<Any>
  ) = batchLoad(keys, consistentReads = EVENTUAL, retryStrategy = DefaultBatchLoadRetryStrategy())

  fun batchLoad(
    keys: Iterable<Any>,
    consistentReads: ConsistentReads
  ) = batchLoad(
    keys, consistentReads = consistentReads, retryStrategy = DefaultBatchLoadRetryStrategy()
  )

  fun batchLoad(
    keys: Iterable<Any>,
    retryStrategy: BatchLoadRetryStrategy
  ) = batchLoad(
    keys, consistentReads = EVENTUAL, retryStrategy = retryStrategy
  )

  @CheckReturnValue
  fun batchWrite(
    writeSet: BatchWriteSet
  ) = batchWrite(writeSet, retryStrategy = DefaultBatchWriteRetryStrategy())
}

/**
 * A collection of views on a DynamoDB table that makes it easy to model heterogeneous items
 * using strongly typed data classes.
 */
interface LogicalTable<RI : Any> :
  View<RI, RI>,
  InlineView.Factory,
  SecondaryIndex.Factory {

  /** [type] must be a key type or item type of one of the views of this table. */
  fun <T : Any> codec(type: KClass<T>): Codec<T, RI>

  interface Factory {
    fun <T : LogicalTable<RI>, RI : Any> logicalTable(
      tableType: KClass<T>
    ): T
  }
}

interface InlineView<K : Any, I : Any> : View<K, I>, Scannable<K, I>, Queryable<K, I> {

  interface Factory {
    fun <K : Any, I : Any> inlineView(
      keyType: KClass<K>,
      itemType: KClass<I>
    ): InlineView<K, I>
  }
}

interface SecondaryIndex<K : Any, I : Any> : Scannable<K, I>, Queryable<K, I> {

  interface Factory {
    fun <K : Any, I : Any> secondaryIndex(
      keyType: KClass<K>,
      itemType: KClass<I>
    ): SecondaryIndex<K, I>
  }
}
