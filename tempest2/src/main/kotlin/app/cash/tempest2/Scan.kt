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

interface Scannable<K : Any, I : Any> {
  /**
   * Scans up to the [pageSize] items or a maximum of 1 MB of data. This limit applies before the
   * filter expression is evaluated.
   */
  fun scan(
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
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
    config.pageSize,
    config.consistentRead,
    config.filterExpression,
    initialOffset
  )

  /**
   * Executes a scan and returns a sequence of pages that contains all results, regardless of page size.
   * New pages will be fetched as needed when the resulting sequence is enumerated.
   */
  fun scanAll(
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
    initialOffset: Offset<K>? = null,
  ): Sequence<Page<K, I>>

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun scanAll() = scanAll(
    ScanConfig.Builder().build(),
    initialOffset = null
  )

  fun scanAll(initialOffset: Offset<K>?) = scanAll(
    ScanConfig.Builder().build(),
    initialOffset = initialOffset
  )

  fun scanAll(config: ScanConfig) = scanAll(
    config,
    initialOffset = null
  )

  fun scanAll(config: ScanConfig, initialOffset: Offset<K>?): Sequence<Page<K, I>> {
    return scanAll(
      config.pageSize,
      config.consistentRead,
      config.filterExpression,
      initialOffset
    )
  }

  /**
   * Executes a scan and returns a sequence that contains all results, regardless of page size.
   * New pages will be fetched as needed when the resulting sequence is enumerated.
   */
  fun scanAllContents(
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
    initialOffset: Offset<K>? = null,
  ): Sequence<I>

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun scanAllContents() = scanAllContents(
    ScanConfig.Builder().build(),
    initialOffset = null
  )

  fun scanAllContents(initialOffset: Offset<K>?) = scanAllContents(
    ScanConfig.Builder().build(),
    initialOffset = initialOffset
  )

  fun scanAllContents(config: ScanConfig) = scanAllContents(
    config,
    initialOffset = null
  )

  fun scanAllContents(config: ScanConfig, initialOffset: Offset<K>?): Sequence<I> {
    return scanAllContents(
      config.pageSize,
      config.consistentRead,
      config.filterExpression,
      initialOffset
    )
  }
}

data class ScanConfig internal constructor(
  val pageSize: Int,
  val consistentRead: Boolean,
  val filterExpression: Expression?
) {
  class Builder {
    private var pageSize = 100
    private var consistentRead = false
    private var filterExpression: Expression? = null

    fun pageSize(pageSize: Int) = apply { this.pageSize = pageSize }

    fun consistentRead(consistentRead: Boolean) = apply { this.consistentRead = consistentRead }

    fun filterExpression(filterExpression: Expression) =
      apply { this.filterExpression = filterExpression }

    fun build() = ScanConfig(
      pageSize,
      consistentRead,
      filterExpression
    )
  }
}
