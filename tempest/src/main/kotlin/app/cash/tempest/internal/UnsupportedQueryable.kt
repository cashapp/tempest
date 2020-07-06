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

package app.cash.tempest.internal

import app.cash.tempest.FilterExpression
import app.cash.tempest.KeyCondition
import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.Queryable
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import java.lang.UnsupportedOperationException
import kotlin.reflect.KClass

internal class UnsupportedQueryable<K : Any, I : Any>(
  private val rawType: KClass<*>
) : Queryable<K, I> {
  override fun query(
    keyCondition: KeyCondition<K>,
    consistentRead: Boolean,
    asc: Boolean,
    pageSize: Int,
    returnConsumedCapacity: ReturnConsumedCapacity,
    filterExpression: FilterExpression?,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    throw UnsupportedOperationException("Require $rawType to have a range key. You can query a table or an index only if it has a composite primary key (partition key and sort key)")
  }
}
