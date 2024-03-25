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
import software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity

interface Queryable<K : Any, I : Any> {

  /**
   * Reads up to the [pageSize] items or a maximum of 1 MB of data. This limit applies before the
   * filter expression is evaluated.
   */
  fun query(
    keyCondition: KeyCondition<K>,
    asc: Boolean = true,
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
    initialOffset: Offset<K>? = null,
    returnConsumedCapacity: ReturnConsumedCapacity? = null,
  ): Page<K, I>

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun query(keyCondition: KeyCondition<K>) = query(
    keyCondition,
    config = QueryConfig.Builder().build(),
    initialOffset = null
  )

  fun query(keyCondition: KeyCondition<K>, initialOffset: Offset<K>?) = query(
    keyCondition,
    config = QueryConfig.Builder().build(),
    initialOffset = initialOffset
  )

  fun query(keyCondition: KeyCondition<K>, config: QueryConfig) = query(
    keyCondition,
    config = config,
    initialOffset = null
  )

  fun query(
    keyCondition: KeyCondition<K>,
    config: QueryConfig,
    initialOffset: Offset<K>?,
  ) = query(
    keyCondition,
    config.asc,
    config.pageSize,
    config.consistentRead,
    config.filterExpression,
    initialOffset,
    config.returnConsumedCapacity
  )
}

data class QueryConfig internal constructor(
  val asc: Boolean,
  val pageSize: Int,
  val consistentRead: Boolean,
  val filterExpression: Expression?,
  val returnConsumedCapacity: ReturnConsumedCapacity?,
) {
  class Builder {
    private var asc = true
    private var pageSize = 100
    private var consistentRead = false
    private var filterExpression: Expression? = null
    private var returnConsumedCapacity: ReturnConsumedCapacity? = null

    fun asc(asc: Boolean) = apply { this.asc = asc }

    fun pageSize(pageSize: Int) = apply { this.pageSize = pageSize }

    fun consistentRead(consistentRead: Boolean) = apply { this.consistentRead = consistentRead }

    fun filterExpression(filterExpression: Expression) =
      apply { this.filterExpression = filterExpression }

    fun returnConsumedCapacity(returnConsumedCapacity: ReturnConsumedCapacity) =
      apply { this.returnConsumedCapacity = returnConsumedCapacity }

    fun build() = QueryConfig(
      asc,
      pageSize,
      consistentRead,
      filterExpression,
      returnConsumedCapacity
    )
  }
}

/**
 * Used to query a table or an index.
 */
sealed class KeyCondition<K>

/**
 * Applies equality condition on the hash key and the following condition on the range key
 * - begins_with (a, substr)— true if the value of attribute a begins with a particular substring.
 */
data class BeginsWith<K>(
  val prefix: K,
) : KeyCondition<K>()

/**
 * Applies equality condition on the hash key and the following condition on the range key
 * - a BETWEEN b AND c — true if a is greater than or equal to b, and less than or equal to c.
 */
data class Between<K>(
  val startInclusive: K,
  val endInclusive: K,
) : KeyCondition<K>()
