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

package app.cash.tempest

/**
 * Maps an item class property to one or more attributes in a DynamoDB table.
 *
 * If this mapped to a primary range key, it must have a prefix. Tempest
 * automatically adds the prefix before database writes and removes it after
 * database reads. To disable this behavior (not typesafe!), set `noPrefix` to true.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Attribute(
  val name: String = "",
  val names: Array<String> = [],
  val prefix: String = "",
  val noPrefix: Boolean = false,
)

/**
 * Maps an key class to a global or local secondary index in a DynamoDB table.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ForIndex(
  val name: String = ""
)
