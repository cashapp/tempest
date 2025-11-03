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
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.io.Closeable

internal class HybridLogicalDbImpl(
  private val delegate: LogicalDb,
  override val hybridConfig: HybridConfig,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val executorService: ExecutorService
) : HybridLogicalDb, LogicalDb by delegate, Closeable {

  private val archivalService = ArchivalService(delegate, s3Client, objectMapper, hybridConfig)

  override suspend fun archiveOldData(): ArchivalResult {
    return archivalService.archiveOldData(dryRun = false)
  }

  override suspend fun archiveOldDataDryRun(): ArchivalResult {
    return archivalService.archiveOldData(dryRun = true)
  }

  override fun close() {
    executorService.shutdown()
    // Wait a reasonable amount of time for termination
    if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
      executorService.shutdownNow()
    }
  }
  
  companion object {
    fun <DB : HybridLogicalDb> create(
      dbType: KClass<DB>,
      regularDb: LogicalDb,
      hybridConfig: HybridConfig
    ): DB {

      // Create S3 client
      val s3ClientBuilder = AmazonS3ClientBuilder.standard()
        .withRegion(hybridConfig.s3Config.region)

      if (hybridConfig.s3Config.accessKey != null && hybridConfig.s3Config.secretKey != null) {
        val credentials = BasicAWSCredentials(
          hybridConfig.s3Config.accessKey,
          hybridConfig.s3Config.secretKey
        )
        s3ClientBuilder.withCredentials(AWSStaticCredentialsProvider(credentials))
      }

      val s3Client = s3ClientBuilder.build()
      val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

      // Create executor service for parallel S3 operations
      val executorService = Executors.newFixedThreadPool(hybridConfig.performanceConfig.parallelS3Reads)

      val hybridDb = HybridLogicalDbImpl(
        regularDb,
        hybridConfig,
        s3Client,
        objectMapper,
        executorService
      )

      // Create proxy that wraps table methods to return hybrid tables
      return Proxy.newProxyInstance(
        dbType.java.classLoader,
        arrayOf(dbType.java),
        HybridDbProxy(hybridDb, regularDb, s3Client, objectMapper, hybridConfig, executorService)
      ) as DB
    }
  }
}
