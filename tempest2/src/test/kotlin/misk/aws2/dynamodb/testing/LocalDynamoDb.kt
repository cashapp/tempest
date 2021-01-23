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

package misk.aws2.dynamodb.testing

import okhttp3.HttpUrl
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A DynamoDB test server running in-process or in a local Docker container.
 */
@Singleton
internal class LocalDynamoDb internal constructor(port: Int) {

  @Inject constructor() : this(pickPort())

  val url = HttpUrl.Builder()
    .scheme("http")
    .host("localhost")
    .port(port)
    .build()

  val awsCredentialsProvider = StaticCredentialsProvider.create(
    AwsBasicCredentials.create("key", "secret")
  )

  fun connect(): DynamoDbClient {
    return DynamoDbClient.builder()
      // The values that you supply for the AWS access key and the Region are only used to name
      // the database file.
      .credentialsProvider(awsCredentialsProvider)
      .region(Region.US_WEST_2)
      .endpointOverride(url.toUri())
      .build()
  }

  companion object {
    internal fun pickPort(): Int {
      // There is a tolerable chance of flaky tests caused by port collision.
      return 58000 + (ProcessHandle.current().pid() % 1000).toInt()
    }
  }
}
