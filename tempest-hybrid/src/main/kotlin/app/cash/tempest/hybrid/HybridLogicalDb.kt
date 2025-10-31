package app.cash.tempest.hybrid

import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import kotlin.reflect.KClass

/**
 * Hybrid LogicalDb that supports pointer-based S3 storage
 */
interface HybridLogicalDb : LogicalDb {

  val hybridConfig: HybridConfig

  /**
   * Archive old data to S3 based on configured age threshold
   */
  suspend fun archiveOldData(): ArchivalResult

  /**
   * Run archival in dry-run mode to see what would be archived without making changes
   */
  suspend fun archiveOldDataDryRun(): ArchivalResult
  
  companion object {
    fun <DB : HybridLogicalDb> create(
      dbType: KClass<DB>,
      regularDb: LogicalDb,
      hybridConfig: HybridConfig
    ): DB {
      return HybridLogicalDbImpl.create(dbType, regularDb, hybridConfig)
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
