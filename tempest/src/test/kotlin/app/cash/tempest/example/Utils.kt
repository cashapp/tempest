/*
 * Copyright 2020 Square Inc.
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

package app.cash.tempest.example

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import java.time.Duration
import java.time.LocalDate

internal class DurationTypeConverter : DynamoDBTypeConverter<String, Duration> {
  override fun unconvert(string: String): Duration {
    return Duration.parse(string)
  }

  override fun convert(duration: Duration): String {
    return duration.toString()
  }
}

internal class LocalDateTypeConverter : DynamoDBTypeConverter<String, LocalDate> {
  override fun unconvert(string: String): LocalDate {
    return LocalDate.parse(string)
  }

  override fun convert(localDate: LocalDate): String {
    return localDate.toString()
  }
}
