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
 * Result of an archival operation.
 */
data class ArchivalResult(
  /**
   * Total number of items that were processed (scanned).
   */
  val itemsProcessed: Int,

  /**
   * Number of items that were successfully archived to S3.
   */
  val itemsArchived: Int,

  /**
   * List of any errors that occurred during archival.
   */
  val errors: List<String> = emptyList()
) {
  /**
   * Whether the archival completed without any errors.
   */
  val success: Boolean = errors.isEmpty()

  /**
   * Number of items that were skipped (already archived or not old enough).
   */
  val itemsSkipped: Int = itemsProcessed - itemsArchived

  fun toSummaryString(): String {
    return buildString {
      appendLine("Archival Result:")
      appendLine("  Items processed: $itemsProcessed")
      appendLine("  Items archived: $itemsArchived")
      appendLine("  Items skipped: $itemsSkipped")
      if (errors.isNotEmpty()) {
        appendLine("  Errors: ${errors.size}")
        errors.take(5).forEach { error ->
          appendLine("    - $error")
        }
        if (errors.size > 5) {
          appendLine("    ... and ${errors.size - 5} more")
        }
      }
    }
  }
}