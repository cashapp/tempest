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

/** Configuration for pointer-based hybrid storage */
data class HybridConfig(
  val s3Config: S3Config,
  val errorStrategy: ErrorStrategy = ErrorStrategy.FAIL_FAST,
  val metrics: HybridMetrics? = null, // Optional metrics collection - null means no metrics
) {

  data class S3Config(
    val bucketName: String,
    val keyPrefix: String = "", // Currently unused - could be used for S3 key generation
    val region: String? = null, // Currently only used for logging
  )

  /**
   * Defines how to handle S3 hydration failures.
   */
  enum class ErrorStrategy {
    /**
     * Throw an exception when S3 hydration fails.
     * This is the safest option as it prevents corrupted data from propagating.
     */
    FAIL_FAST,

    /**
     * Return the original pointer item when S3 hydration fails.
     * WARNING: This will likely cause deserialization errors downstream.
     * Only use if you have error handling at the application level.
     */
    RETURN_POINTER,

    /**
     * Skip items that fail to hydrate (return null for single items, exclude from collections).
     * WARNING: This silently drops data and may cause unexpected behavior.
     */
    SKIP_FAILED
  }
}