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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import software.amazon.awssdk.enhanced.dynamodb.Expression

interface AsyncScannable<K : Any, I : Any> {
  /**
   * Scans up to the [pageSize] items or a maximum of 1 MB of data. This limit applies before the
   * filter expression is evaluated.
   */
  suspend fun scan(
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
    initialOffset: Offset<K>? = null
  ): Page<K, I>

  // Overloaded functions for Java callers (Kotlin interfaces do not support `@JvmOverloads`).

  fun scanAsync(
    pageSize: Int = 100,
    consistentRead: Boolean = false,
    filterExpression: Expression? = null,
    initialOffset: Offset<K>? = null
  ) = GlobalScope.future { scan(pageSize, consistentRead, filterExpression, initialOffset) }

  fun scanAsync() = scanAsync(
    ScanConfig.Builder().build(),
    initialOffset = null
  )

  fun scanAsync(initialOffset: Offset<K>?) = scanAsync(
    ScanConfig.Builder().build(),
    initialOffset = initialOffset
  )

  fun scanAsync(config: ScanConfig) = scanAsync(
    config,
    initialOffset = null
  )

  fun scanAsync(config: ScanConfig, initialOffset: Offset<K>?) = scanAsync(
    config.pageSize,
    config.consistentRead,
    config.filterExpression,
    initialOffset
  )
}
