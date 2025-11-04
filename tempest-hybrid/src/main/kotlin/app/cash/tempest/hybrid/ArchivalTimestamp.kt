/*
 * Copyright 2024 Square Inc.
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
package app.cash.tempest.hybrid

/**
 * Marks the timestamp field that should be used to determine if an item
 * is old enough to be archived to S3.
 *
 * The field must be of type Instant or a type that can be converted to Instant.
 * This timestamp is compared against the archiveAfterDays configuration to
 * determine if the item should be archived.
 *
 * Example:
 * ```
 * @HybridTable(archiveAfterDays = 180)
 * data class OrderItem(
 *   val orderId: String,
 *
 *   @ArchivalTimestamp
 *   val createdAt: Instant,  // Items with createdAt > 180 days ago will be archived
 *
 *   val orderData: String
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ArchivalTimestamp