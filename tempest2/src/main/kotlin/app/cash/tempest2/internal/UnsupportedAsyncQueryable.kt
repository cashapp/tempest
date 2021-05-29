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

package app.cash.tempest2.internal

import app.cash.tempest2.AsyncQueryable
import app.cash.tempest2.KeyCondition
import app.cash.tempest2.Offset
import app.cash.tempest2.Page
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import software.amazon.awssdk.enhanced.dynamodb.Expression
import kotlin.reflect.KClass

internal class UnsupportedAsyncQueryable<K : Any, I : Any>(
  private val rawType: KClass<*>
) : AsyncQueryable<K, I> {
  override fun queryAsync(
    keyCondition: KeyCondition<K>,
    asc: Boolean,
    pageSize: Int,
    consistentRead: Boolean,
    filterExpression: Expression?,
    initialOffset: Offset<K>?
  ): Publisher<Page<K, I>> {
    return flow<Page<K, I>> {
      throw UnsupportedOperationException("Require $rawType to have a range key. You can query a table or an index only if it has a composite primary key (partition key and sort key)")
    }.asPublisher()
  }
}
