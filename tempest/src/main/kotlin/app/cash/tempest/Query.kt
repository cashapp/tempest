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

import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity.NONE

interface Queryable<K : Any, I : Any> {
  /** Reads up to the [pageSize] items or a maximum of 1 MB of data. */
  fun query(
    startInclusive: K,
    endExclusive: K,
    consistentRead: Boolean = false,
    asc: Boolean = true,
    pageSize: Int = 100,
    returnConsumedCapacity: ReturnConsumedCapacity = NONE,
    initialOffset: Offset<K>? = null
  ): Page<K, I>
}
