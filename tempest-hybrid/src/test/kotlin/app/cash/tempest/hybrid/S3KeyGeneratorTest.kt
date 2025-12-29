package app.cash.tempest.hybrid

import app.cash.tempest2.Attribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import kotlin.reflect.full.memberProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class S3KeyGeneratorTest {

  @Test
  fun `generates S3 key with partition and sort key`() {
    // Given
    val item = TestItemWithBothKeys(
      partitionKey = "USER#123",
      sortKey = "TRANSACTION#2024-01-15"
    )
    val template = "{tableName}/{partitionKey}/{sortKey}"
    val tableName = "transactions"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("transactions/USER#123/TRANSACTION#2024-01-15.json.gz")
  }

  @Test
  fun `generates S3 key with partition key only`() {
    // Given
    val item = TestItemWithPartitionKeyOnly(
      partitionKey = "CONFIG#GLOBAL"
    )
    val template = "{tableName}/{partitionKey}"
    val tableName = "config"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("config/CONFIG#GLOBAL.json.gz")
  }

  @Test
  fun `supports legacy template variables`() {
    // Given
    val item = TestItemWithBothKeys(
      partitionKey = "USER#123",
      sortKey = "PROFILE"
    )
    val template = "{table}/{pk}/{sk}"
    val tableName = "users"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("users/USER#123/PROFILE.json.gz")
  }

  @Test
  fun `sanitizes problematic characters in keys`() {
    // Given
    val item = TestItemWithBothKeys(
      partitionKey = "USER:123<test>",
      sortKey = "DATA|2024\\01\\15"
    )
    val template = "{tableName}/{partitionKey}/{sortKey}"
    val tableName = "data"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("data/USER_123_test/DATA_2024_01_15.json.gz")
  }

  @Test
  fun `adds json gz extension if not present`() {
    // Given
    val item = TestItemWithPartitionKeyOnly(
      partitionKey = "DOC#123"
    )
    val template = "{tableName}/{partitionKey}.json"
    val tableName = "documents"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("documents/DOC#123.json")
  }

  @Test
  fun `cleans up double slashes in path`() {
    // Given
    val item = TestItemWithBothKeys(
      partitionKey = "USER#123",
      sortKey = ""  // Empty sort key
    )
    val template = "{tableName}//{partitionKey}//{sortKey}//"
    val tableName = "users"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("users/USER#123.json.gz")
  }

  @Test
  fun `extracts keys using DynamoDB annotations`() {
    // Given
    val item = TestItemWithDynamoDBAnnotations(
      myHashKey = "HASH#VALUE",
      myRangeKey = "RANGE#VALUE"
    )

    // When
    val (partitionKey, sortKey) = S3KeyGenerator.extractKeys(item)

    // Then
    assertThat(partitionKey).isEqualTo("HASH#VALUE")
    assertThat(sortKey).isEqualTo("RANGE#VALUE")
  }

  @Test
  fun `extracts keys using Tempest Attribute annotations`() {
    // Given
    val item = TestItemWithTempestAnnotations(
      customPartition = "PARTITION#123",
      customSort = "SORT#456"
    )

    // When
    val (partitionKey, sortKey) = S3KeyGenerator.extractKeys(item)

    // Then
    assertThat(partitionKey).isEqualTo("PARTITION#123")
    assertThat(sortKey).isEqualTo("SORT#456")
  }

  @Test
  fun `throws exception when partition key not found`() {
    // Given
    val item = TestItemWithoutKeys()

    // When/Then
    val exception = assertThrows<IllegalStateException> {
      S3KeyGenerator.extractKeys(item)
    }

    assertThat(exception.message).contains("Could not find partition key")
  }

  @Test
  fun `identifies key fields correctly`() {
    // Given
    val itemClass = TestItemWithDynamoDBAnnotations::class
    val properties = itemClass.memberProperties

    // When
    val hashKeyProp = properties.find { it.name == "myHashKey" }
    val rangeKeyProp = properties.find { it.name == "myRangeKey" }
    val normalProp = properties.find { it.name == "data" }

    // Then
    assertThat(S3KeyGenerator.isKeyField(hashKeyProp!!)).isTrue()
    assertThat(S3KeyGenerator.isKeyField(rangeKeyProp!!)).isTrue()
    assertThat(S3KeyGenerator.isKeyField(normalProp!!)).isFalse()
  }

  @Test
  fun `handles null sort key`() {
    // Given
    val item = TestItemWithOptionalSortKey(
      partitionKey = "USER#789",
      sortKey = null
    )
    val template = "{tableName}/{partitionKey}/{sortKey}"
    val tableName = "users"

    // When
    val s3Key = S3KeyGenerator.generateS3Key(item, template, tableName)

    // Then
    assertThat(s3Key).isEqualTo("users/USER#789.json.gz")
  }
}

// Test data classes moved outside to be top-level classes for proper reflection
data class TestItemWithBothKeys(
  @DynamoDBHashKey
  val partitionKey: String,
  @DynamoDBRangeKey
  val sortKey: String
)

data class TestItemWithPartitionKeyOnly(
  @DynamoDBHashKey
  val partitionKey: String
)

data class TestItemWithDynamoDBAnnotations(
  @DynamoDBHashKey
  val myHashKey: String,
  @DynamoDBRangeKey
  val myRangeKey: String,
  val data: String = "test"
)

data class TestItemWithTempestAnnotations(
  @Attribute(name = "partition_key")
  val customPartition: String,
  @Attribute(name = "sort_key")
  val customSort: String
)

data class TestItemWithOptionalSortKey(
  @DynamoDBHashKey
  val partitionKey: String,
  @DynamoDBRangeKey
  val sortKey: String? = null
)

class TestItemWithoutKeys {
  val someField: String = "value"
}