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

internal class HybridLogicalDbImpl(
  private val delegate: LogicalDb,
  override val hybridConfig: HybridConfig,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper
) : HybridLogicalDb, LogicalDb by delegate {

  private val archivalService = ArchivalService(delegate, s3Client, objectMapper, hybridConfig)

  override suspend fun archiveOldData(): ArchivalResult {
    return archivalService.archiveOldData(dryRun = false)
  }

  override suspend fun archiveOldDataDryRun(): ArchivalResult {
    return archivalService.archiveOldData(dryRun = true)
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
      
      val hybridDb = HybridLogicalDbImpl(regularDb, hybridConfig, s3Client, objectMapper)
      
      // Create proxy that wraps table methods to return hybrid tables
      return Proxy.newProxyInstance(
        dbType.java.classLoader,
        arrayOf(dbType.java),
        HybridDbProxy(hybridDb, regularDb, s3Client, objectMapper, hybridConfig)
      ) as DB
    }
  }
}
