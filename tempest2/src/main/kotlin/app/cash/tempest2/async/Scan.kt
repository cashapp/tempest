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

package app.cash.tempest2.async

import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import app.cash.tempest2.ScanConfig
import software.amazon.awssdk.enhanced.dynamodb.Expression

interface Scannable<K : Any, I : Any> {
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

  suspend fun scan() = scan(
    ScanConfig.Builder().build(),
    initialOffset = null
  )

  suspend fun scan(initialOffset: Offset<K>?) = scan(
    ScanConfig.Builder().build(),
    initialOffset = initialOffset
  )

  suspend fun scan(config: ScanConfig) = scan(
    config,
    initialOffset = null
  )

  suspend fun scan(config: ScanConfig, initialOffset: Offset<K>?) = scan(
    config.pageSize,
    config.consistentRead,
    config.filterExpression,
    initialOffset
  )
}
