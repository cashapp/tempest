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

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity.NONE

interface Scannable<K : Any, I : Any> {
  /**
   * Scans up to the [pageSize] items or a maximum of 1 MB of data. This limit applies before the
   * filter expression is evaluated.
   */
  fun scan(
    workerId: WorkerId = WorkerId.SEQUENTIAL,
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    returnConsumedCapacity: ReturnConsumedCapacity = NONE,
    filterExpression: FilterExpression? = null,
    initialOffset: Offset<K>? = null
  ): Page<K, I>

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun scan() = scan(
    ScanConfig.Builder().build(),
    initialOffset = null
  )

  fun scan(initialOffset: Offset<K>?) = scan(
    ScanConfig.Builder().build(),
    initialOffset = initialOffset
  )

  fun scan(config: ScanConfig) = scan(
    config,
    initialOffset = null
  )

  fun scan(config: ScanConfig, initialOffset: Offset<K>?) = scan(
    config.workerId,
    config.pageSize,
    config.consistentRead,
    config.returnConsumedCapacity,
    config.filterExpression,
    initialOffset
  )
}

data class ScanConfig internal constructor(
  val workerId: WorkerId,
  val pageSize: Int,
  val consistentRead: Boolean,
  val returnConsumedCapacity: ReturnConsumedCapacity,
  val filterExpression: FilterExpression?
) {
  class Builder {
    private var workerId = WorkerId.SEQUENTIAL
    private var pageSize = 100
    private var consistentRead = false
    private var returnConsumedCapacity = NONE
    private var filterExpression: FilterExpression? = null

    fun workerId(workerId: WorkerId) = apply { this.workerId = workerId }

    fun pageSize(pageSize: Int) = apply { this.pageSize = pageSize }

    fun consistentRead(consistentRead: Boolean) = apply { this.consistentRead = consistentRead }

    fun returnConsumedCapacity(returnConsumedCapacity: ReturnConsumedCapacity) =
      apply { this.returnConsumedCapacity = returnConsumedCapacity }

    fun filterExpression(filterExpression: FilterExpression) =
      apply { this.filterExpression = filterExpression }

    fun build() = ScanConfig(
      workerId,
      pageSize,
      consistentRead,
      returnConsumedCapacity,
      filterExpression
    )
  }
}

/**
 * By default, the Scan operation processes data sequentially. Amazon DynamoDB returns data to the
 * application in 1 MB increments, and an application performs additional Scan operations to
 * retrieve the next 1 MB of data.
 *
 * The larger the table or index being scanned, the more time the Scan takes to complete. In
 * addition, a sequential Scan might not always be able to fully use the provisioned read throughput
 * capacity: Even though DynamoDB distributes a large table's data across multiple physical
 * partitions, a Scan operation can only read one partition at a time. For this reason, the
 * throughput of a Scan is constrained by the maximum throughput of a single partition.
 *
 * To address these issues, the Scan operation can logically divide a table or secondary index into
 * multiple segments, with multiple application workers scanning the segments in parallel. Each
 * worker can be a thread (in programming languages that support multithreading) or an operating
 * system process. To perform a parallel scan, each worker issues its own Scan request with an
 * unique [WorkerId].
 */
data class WorkerId(
  /**
   * A segment to be scanned by a particular worker. Each worker should use a different value for
   * Segment.
   *
   * Segments are zero-based, so the first number is always 0.
   */
  val segment: Int = 0,
  /**
   * The total number of segments for the parallel scan. This value must be the same as the number
   * of workers that your application will use.
   */
  val totalSegments: Int = 1
) {
  init {
    require(segment < totalSegments) { "Expect $segment to be less than $totalSegments" }
  }

  companion object {
    @JvmField
    val SEQUENTIAL = WorkerId(0, 1)
  }
}

/**
 * If you need to further refine the Scan results, you can optionally provide a filter expression.
 * A filter expression determines which items within the Scan results should be returned to you.
 * All of the other results are discarded.
 *
 * A filter expression is applied after a Scan finishes but before the results are returned.
 * Therefore, a Scan consumes the same amount of read capacity, regardless of whether a filter
 * expression is present.
 */
data class FilterExpression @JvmOverloads constructor(
  /**
   * The syntax for a filter expression is identical to that of a condition expression. Filter
   * expressions can use the same comparators, functions, and logical operators as a condition
   * expression. For more information, [Condition Expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ConditionExpressions.html).
   */
  val expression: String,
  /**
   * Expression attribute values in Amazon DynamoDB are substitutes for the actual values that you
   * want to compare—values that you might not know until runtime. An expression attribute value
   * must begin with a colon (:) and be followed by one or more alphanumeric characters.
   */
  val attributeValues: Map<String, AttributeValue> = emptyMap()
)
