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
  val performanceConfig: PerformanceConfig = PerformanceConfig(),
) {

  data class S3Config(
    val bucketName: String,
    val keyPrefix: String = "", // Currently unused - could be used for S3 key generation
    val region: String? = null, // Currently only used for logging
  )

  data class PerformanceConfig(
    val maxConcurrentS3Reads: Int = 10, // Maximum concurrent S3 reads (0 = sequential)
  )
}