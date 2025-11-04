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
 * Marks a DynamoDB table item class for hybrid storage with S3 archival.
 *
 * Items older than [archiveAfterDays] will be moved to S3, leaving only
 * a pointer in DynamoDB with the keys and S3 location.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HybridTable(
  /**
   * Number of days after which items should be archived to S3.
   * Based on the field marked with @ArchivalTimestamp.
   */
  val archiveAfterDays: Int = 180,

  /**
   * Template for generating S3 keys. Available variables:
   * - {tableName}: The DynamoDB table name
   * - {partitionKey}: The partition key value
   * - {sortKey}: The sort key value (empty string if no sort key)
   *
   * Default template creates deterministic keys based only on the item's keys.
   * Examples:
   * - Single key table: "archive/{tableName}/{partitionKey}.json.gz"
   * - Composite key table: "archive/{tableName}/{partitionKey}/{sortKey}.json.gz"
   *
   * IMPORTANT: Do NOT include timestamps or mutable fields in the template.
   * The S3 key must be deterministic and recreatable from the DynamoDB keys alone.
   */
  val s3KeyTemplate: String = "{tableName}/{partitionKey}/{sortKey}"
)