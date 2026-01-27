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

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Configuration for pointer-based hybrid storage */
data class HybridConfig(
  val s3Config: S3Config,
  val errorStrategy: ErrorStrategy = ErrorStrategy.FAIL_FAST,
  val metrics: HybridMetrics? = null, // Optional metrics collection - null means no metrics
  val retryConfig: RetryConfig = RetryConfig() // New field with default (disabled)
) {

  data class S3Config(
    val bucketName: String,
    val keyPrefix: String = "", // Prefix prepended to S3 keys during archival (e.g., "dynamodb/", "archive/2024/")
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

  /**
   * Configuration for S3 operation retry behavior.
   *
   * @property enabled Whether retry logic is enabled (default: false for backward compatibility)
   * @property maxAttempts Maximum number of attempts including initial (default: 3)
   * @property initialDelayMs Initial delay in milliseconds before first retry (default: 100)
   * @property maxDelayMs Maximum delay in milliseconds between retries (default: 5000)
   * @property retryableExceptions List of exception types to retry (default: network/timeout errors)
   */
  data class RetryConfig(
    val enabled: Boolean = false,
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 5000,
    val retryableExceptions: Set<Class<out Exception>> = setOf(
      IOException::class.java,
      SocketTimeoutException::class.java,
      ConnectException::class.java
    )
  ) {
    init {
      require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
      require(initialDelayMs > 0) { "initialDelayMs must be positive" }
      require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
    }
  }
}