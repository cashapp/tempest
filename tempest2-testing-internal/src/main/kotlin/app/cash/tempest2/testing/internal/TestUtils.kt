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

package app.cash.tempest2.testing.internal

import app.cash.tempest2.testing.TestTable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI

fun pickRandomPort(): Int {
  ServerSocket(0).use { socket -> return socket.localPort }
}

private val AWS_CREDENTIALS_PROVIDER = StaticCredentialsProvider.create(
  AwsBasicCredentials.create("key", "secret")
)

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

fun buildDynamoDb(port: Int): DynamoDbClient = buildDynamoDb(hostName(port), port)

fun buildDynamoDb(host: String, port: Int): DynamoDbClient {
  return DynamoDbClient.builder()
    // The values that you supply for the AWS access key and the Region are only used to name
    // the database file.
    .credentialsProvider(AWS_CREDENTIALS_PROVIDER)
    .region(Region.US_WEST_2)
    .endpointOverride(URI.create("http://$host:$port"))
    .build()
}

fun buildAsyncDynamoDb(port: Int): DynamoDbAsyncClient = buildAsyncDynamoDb(hostName(port), port)

fun buildAsyncDynamoDb(host: String, port: Int): DynamoDbAsyncClient {
  return DynamoDbAsyncClient.builder()
    // The values that you supply for the AWS access key and the Region are only used to name
    // the database file.
    .credentialsProvider(AWS_CREDENTIALS_PROVIDER)
    .region(Region.US_WEST_2)
    .endpointOverride(URI.create("http://$host:$port"))
    .build()
}

fun buildDynamoDbStreams(port: Int): DynamoDbStreamsClient = buildDynamoDbStreams(hostName(port), port)

fun buildDynamoDbStreams(host: String, port: Int): DynamoDbStreamsClient {
  return DynamoDbStreamsClient.builder()
    // The values that you supply for the AWS access key and the Region are only used to name
    // the database file.
    .credentialsProvider(AWS_CREDENTIALS_PROVIDER)
    .region(Region.US_WEST_2)
    .endpointOverride(URI.create("http://$host:$port"))
    .build()
}

fun buildAsyncDynamoDbStreams(port: Int): DynamoDbStreamsAsyncClient =
  buildAsyncDynamoDbStreams(hostName(port), port)


fun buildAsyncDynamoDbStreams(host: String, port: Int): DynamoDbStreamsAsyncClient {
  return DynamoDbStreamsAsyncClient.builder()
    // The values that you supply for the AWS access key and the Region are only used to name
    // the database file.
    .credentialsProvider(AWS_CREDENTIALS_PROVIDER)
    .region(Region.US_WEST_2)
    .endpointOverride(URI.create("http://$host:$port"))
    .build()
}

fun DynamoDbClient.createTable(
  table: TestTable
) {
  val enhancedClient = DynamoDbEnhancedClient.builder()
    .dynamoDbClient(this)
    .build()
  var tableRequest = CreateTableEnhancedRequest.builder()
    // Provisioned throughput needs to be specified when creating the table. However,
    // DynamoDB Local ignores your provisioned throughput settings. The values that you specify
    // when you call CreateTable and UpdateTable have no effect. In addition, DynamoDB Local
    // does not throttle read or write activity.
    .provisionedThroughput(
      ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build()
    )
    .build()
  tableRequest = table.configureTable(tableRequest)
  enhancedClient.table(table.tableName, TableSchema.fromClass(table.tableClass.java))
    .createTable(tableRequest)
}
