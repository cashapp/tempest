/*
 * Copyright 2021 Square Inc.
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

package app.cash.tempest.testing.internal

import app.cash.tempest.testing.TestTable
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

fun allocateRandomPort(): ServerSocket {
  val socket = ServerSocket(0)
  Runtime.getRuntime().addShutdownHook(
    Thread { socket.close() }
  )
  return socket
}

private const val CONNECT_TIMEOUT_MILLIS = 1_000

fun isServerListening(host: String, port: Int): Boolean {
  return Socket().use {
    try {
      it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
      true
    } catch (e: Exception) {
      false
    }
  }
}

fun hostName(port: Int): String {
  return if (isServerListening("host.docker.internal", port)) {
    "host.docker.internal"
  } else {
    "localhost"
  }
}

fun buildDynamoDb(host: String, port: Int): AmazonDynamoDB {
  return AmazonDynamoDBClientBuilder.standard()
    // The values that you supply for the AWS access key and the Region are only used to name
    // the database file.
    .withCredentials(AWS_CREDENTIALS_PROVIDER)
    .withEndpointConfiguration(endpointConfiguration(host, port))
    .build()
}

fun buildDynamoDbStreams(host: String, port: Int): AmazonDynamoDBStreams {
  return AmazonDynamoDBStreamsClientBuilder.standard()
    .withCredentials(AWS_CREDENTIALS_PROVIDER)
    .withEndpointConfiguration(endpointConfiguration(host, port))
    .build()
}

private val AWS_CREDENTIALS_PROVIDER = AWSStaticCredentialsProvider(
  BasicAWSCredentials("key", "secret")
)

private fun endpointConfiguration(host: String, port: Int): AwsClientBuilder.EndpointConfiguration {
  return AwsClientBuilder.EndpointConfiguration(
    "http://$host:$port",
    Regions.US_WEST_2.toString()
  )
}

fun AmazonDynamoDB.createTable(table: TestTable) {
  var tableRequest = DynamoDBMapper(this)
    .generateCreateTableRequest(table.tableClass.java)
    // Provisioned throughput needs to be specified when creating the table. However,
    // DynamoDB Local ignores your provisioned throughput settings. The values that you specify
    // when you call CreateTable and UpdateTable have no effect. In addition, DynamoDB Local
    // does not throttle read or write activity.
    .withProvisionedThroughput(ProvisionedThroughput(1L, 1L))
  val globalSecondaryIndexes = tableRequest.globalSecondaryIndexes ?: emptyList()
  for (globalSecondaryIndex in globalSecondaryIndexes) {
    // Provisioned throughput needs to be specified when creating the table.
    globalSecondaryIndex.provisionedThroughput = ProvisionedThroughput(1L, 1L)
  }
  tableRequest = table.configureTable(tableRequest)

  DynamoDB(this).createTable(tableRequest).waitForActive()
}
