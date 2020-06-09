package misk.logicaldb.example

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
