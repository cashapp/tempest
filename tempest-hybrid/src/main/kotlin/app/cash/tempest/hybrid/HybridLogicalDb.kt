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

import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import java.io.Closeable
import kotlin.reflect.KClass

/**
 * Hybrid LogicalDb that supports pointer-based S3 storage
 *
 * IMPORTANT: This interface extends Closeable. You must call close() when done
 * to properly shut down the executor service used for parallel S3 operations.
 */
interface HybridLogicalDb : LogicalDb, Closeable {

  val hybridConfig: HybridConfig

  /**
   * Archive old data to S3 based on configured age threshold
   * @param dryRun If true, simulates archival without making changes
   */
  suspend fun archiveOldData(dryRun: Boolean = false): ArchivalResult
  
  companion object {
    /**
     * Create a HybridLogicalDb with user-provided S3Client and ObjectMapper
     * This is the recommended approach for production use.
     */
    fun create(
      regularDb: LogicalDb,
      s3Client: com.amazonaws.services.s3.AmazonS3,
      hybridConfig: HybridConfig,
      objectMapper: com.fasterxml.jackson.databind.ObjectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    ): HybridLogicalDb {
      return HybridLogicalDbImpl.create(regularDb, s3Client, hybridConfig, objectMapper)
    }

    /**
     * Create a typed HybridLogicalDb (for advanced use)
     */
    fun <DB : HybridLogicalDb> create(
      dbType: KClass<DB>,
      regularDb: LogicalDb,
      s3Client: com.amazonaws.services.s3.AmazonS3,
      hybridConfig: HybridConfig,
      objectMapper: com.fasterxml.jackson.databind.ObjectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    ): DB {
      return HybridLogicalDbImpl.create(dbType, regularDb, s3Client, hybridConfig, objectMapper)
    }
  }
}

/**
 * Hybrid LogicalTable that supports S3 pointer reads
 */
interface HybridLogicalTable<RI : Any> : LogicalTable<RI>

/**
 * Hybrid InlineView that can read from DynamoDB or S3 based on pointer
 */
interface HybridInlineView<K : Any, I : Any> : app.cash.tempest.InlineView<K, I> {
  
  /**
   * Load item from DynamoDB or S3 based on where the data actually is
   */
  fun loadHybrid(key: K): I?
}

/**
 * Result of archival operation
 */
data class ArchivalResult(
  val itemsProcessed: Int,
  val itemsArchived: Int,
  val errors: List<String>
)
