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

import kotlinx.coroutines.reactive.awaitFirst
import org.reactivestreams.Publisher
import software.amazon.awssdk.enhanced.dynamodb.Expression
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity

interface AsyncQueryable<K : Any, I : Any> {

  /**
   * Reads up to the [pageSize] items or a maximum of 1 MB of data. This limit applies before the
   * filter expression is evaluated.
   */
  suspend fun query(
    keyCondition: KeyCondition<K>,
    asc: Boolean = true,
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
    initialOffset: Offset<K>? = null,
    returnConsumedCapacity: ReturnConsumedCapacity? = null,
  ): Page<K, I> = queryAsync(
    keyCondition,
    asc,
    pageSize,
    consistentRead,
    filterExpression,
    initialOffset,
    returnConsumedCapacity
  ).awaitFirst()

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun queryAsync(
    keyCondition: KeyCondition<K>,
    asc: Boolean,
    pageSize: Int,
    consistentRead: Boolean,
    filterExpression: Expression?,
    initialOffset: Offset<K>?,
    returnConsumedCapacity: ReturnConsumedCapacity?,
  ): Publisher<Page<K, I>>

  fun queryAsync(keyCondition: KeyCondition<K>) = queryAsync(
    keyCondition,
    config = QueryConfig.Builder().build(),
    initialOffset = null
  )

  fun queryAsync(keyCondition: KeyCondition<K>, initialOffset: Offset<K>?) = queryAsync(
    keyCondition,
    config = QueryConfig.Builder().build(),
    initialOffset = initialOffset
  )

  fun queryAsync(keyCondition: KeyCondition<K>, config: QueryConfig) = queryAsync(
    keyCondition,
    config = config,
    initialOffset = null
  )

  fun queryAsync(
    keyCondition: KeyCondition<K>,
    config: QueryConfig,
    initialOffset: Offset<K>?,
  ) = queryAsync(
    keyCondition,
    config.asc,
    config.pageSize,
    config.consistentRead,
    config.filterExpression,
    initialOffset,
    config.returnConsumedCapacity
  )
}
