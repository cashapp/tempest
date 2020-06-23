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

import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity

data class Page<K, T>(
  val contents: List<T>,
  val offset: Offset<K>?,
  /**
   * The number of items evaluated, before any filter is applied.
   */
  val scannedCount: Int,
  /**
   * The data returned includes the total provisioned throughput consumed, along with statistics for
   * the table and any indexes involved in the operation. This is only returned if the
   * ReturnConsumedCapacity parameter was specified.
   */
  val consumedCapacity: ConsumedCapacity?
) {
  val hasMorePages: Boolean
    get() = offset != null
}

data class Offset<K>(
  val key: K
)
