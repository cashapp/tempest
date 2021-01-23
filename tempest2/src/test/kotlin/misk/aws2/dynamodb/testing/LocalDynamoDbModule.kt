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

import com.google.inject.Provides
import misk.inject.KAbstractModule
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import javax.inject.Singleton

internal class LocalDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {
  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(localDynamoDb: LocalDynamoDb): DynamoDbClient {
    return localDynamoDb.connect()
  }
}
