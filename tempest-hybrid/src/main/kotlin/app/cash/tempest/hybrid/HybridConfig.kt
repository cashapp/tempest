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

import java.time.Duration

/**
 * Configuration for pointer-based hybrid storage
 */
data class HybridConfig(
  val s3Config: S3Config,
  val archivalConfig: ArchivalConfig,
  val performanceConfig: PerformanceConfig = PerformanceConfig()
) {

  data class S3Config(
    val bucketName: String,
    val keyPrefix: String = "",
    val region: String? = null  // Optional - only needed if creating S3Client internally
  )

  data class ArchivalConfig(
    val enabled: Boolean = true,
    val batchSize: Int? = 25,  // Number of items to archive in parallel
    val scheduleExpression: String? = null  // For future scheduling support
  )

  data class PerformanceConfig(
    val parallelS3Reads: Int = 1,  // Default to 1 for Phase 1 (no parallelism)
    val s3ReadTimeoutMs: Long = 30000,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 100
  )
}
