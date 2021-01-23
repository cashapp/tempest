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

/**
 * Converts values between the mutable `DynamoDbBean` objects that defines the union of all key
 * and value attributes, and specific Tempest values.
 *
 * It is the caller's responsibility to check that the `DynamoDbBean` instance can be safely
 * converted to the target type. If it cannot be, the behavior of this codec is undefined.
 */
interface Codec<A : Any, D : Any> {
  fun toDb(appItem: A): D
  fun toApp(dbItem: D): A
}
