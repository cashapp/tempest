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

import app.cash.tempest.Attribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class S3KeyGeneratorTest {

  // Test domain objects with different key configurations

  data class SingleKeyItem(
    @DynamoDBHashKey
    val userId: String,
    val name: String,
    val createdAt: Instant  // Should NOT affect S3 key
  )

  data class CompositeKeyItem(
    @DynamoDBHashKey
    val customerId: String,

    @DynamoDBRangeKey
    val orderId: String,

    val amount: Double,
    val timestamp: Instant  // Should NOT affect S3 key
  )

  data class TempestAnnotationItem(
    @Attribute(name = "partition_key")
    val pk: String,

    @Attribute(name = "sort_key")
    val sk: String,

    val data: String
  )

  data class ProblematicKeyItem(
    @DynamoDBHashKey
    val key: String,  // Contains special characters

    @DynamoDBRangeKey
    val sort: String,

    val value: String
  )

  @Test
  fun `generates deterministic keys with single partition key`() {
    val item1 = SingleKeyItem("USER_123", "Alice", Instant.now())
    val item2 = SingleKeyItem("USER_123", "Bob", Instant.now().plusSeconds(3600))

    val template = "{tableName}/{partitionKey}"

    val key1 = S3KeyGenerator.generateS3Key(item1, template, "users")
    val key2 = S3KeyGenerator.generateS3Key(item2, template, "users")

    // Same keys should generate same S3 key regardless of other fields
    assertEquals(key1, key2)
    assertEquals("users/USER_123.json.gz", key1)
  }

  @Test
  fun `generates deterministic keys with composite keys`() {
    val item1 = CompositeKeyItem("CUST_456", "ORDER_789", 99.99, Instant.now())
    val item2 = CompositeKeyItem("CUST_456", "ORDER_789", 199.99, Instant.now().plusSeconds(3600))

    val template = "{tableName}/{partitionKey}/{sortKey}"

    val key1 = S3KeyGenerator.generateS3Key(item1, template, "orders")
    val key2 = S3KeyGenerator.generateS3Key(item2, template, "orders")

    // Same keys should generate same S3 key regardless of amount or timestamp
    assertEquals(key1, key2)
    assertEquals("orders/CUST_456/ORDER_789.json.gz", key1)
  }

  @Test
  fun `handles empty sort key correctly`() {
    val item = SingleKeyItem("USER_123", "Alice", Instant.now())

    val template = "{tableName}/{partitionKey}/{sortKey}"

    val key = S3KeyGenerator.generateS3Key(item, template, "users")

    // Empty sort key should result in clean path (no double slashes)
    assertEquals("users/USER_123.json.gz", key)
    assertFalse(key.contains("//"))
  }

  @Test
  fun `supports legacy template variables`() {
    val item = CompositeKeyItem("CUST_456", "ORDER_789", 99.99, Instant.now())

    val legacyTemplate = "{table}/{pk}/{sk}"

    val key = S3KeyGenerator.generateS3Key(item, legacyTemplate, "orders")

    assertEquals("orders/CUST_456/ORDER_789.json.gz", key)
  }

  @Test
  fun `works with Tempest Attribute annotations`() {
    val item = TempestAnnotationItem("PARTITION_VALUE", "SORT_VALUE", "some data")

    val template = "{tableName}/{partitionKey}/{sortKey}"

    val key = S3KeyGenerator.generateS3Key(item, template, "tempest_table")

    assertEquals("tempest_table/PARTITION_VALUE/SORT_VALUE.json.gz", key)
  }

  @Test
  fun `sanitizes problematic characters in keys`() {
    val item = ProblematicKeyItem(
      "user:123<test>",  // Contains : < >
      "order|456*test",  // Contains | *
      "data"
    )

    val template = "{tableName}/{partitionKey}/{sortKey}"

    val key = S3KeyGenerator.generateS3Key(item, template, "test_table")

    // Problematic characters should be replaced with underscores
    assertEquals("test_table/user_123_test_/order_456_test.json.gz", key)
    assertFalse(key.contains(":"))
    assertFalse(key.contains("<"))
    assertFalse(key.contains(">"))
    assertFalse(key.contains("|"))
    assertFalse(key.contains("*"))
  }

  @Test
  fun `adds json gz extension if not present`() {
    val item = SingleKeyItem("USER_123", "Alice", Instant.now())

    // Template without extension
    val template1 = "{tableName}/{partitionKey}"
    val key1 = S3KeyGenerator.generateS3Key(item, template1, "users")
    assertTrue(key1.endsWith(".json.gz"))

    // Template with .json extension
    val template2 = "{tableName}/{partitionKey}.json"
    val key2 = S3KeyGenerator.generateS3Key(item, template2, "users")
    assertTrue(key2.endsWith(".json"))

    // Template with .json.gz extension
    val template3 = "{tableName}/{partitionKey}.json.gz"
    val key3 = S3KeyGenerator.generateS3Key(item, template3, "users")
    assertTrue(key3.endsWith(".json.gz"))
    assertEquals("users/USER_123.json.gz", key3)
  }

  @Test
  fun `cleans up double slashes in template`() {
    val item = CompositeKeyItem("CUST_456", "ORDER_789", 99.99, Instant.now())

    val messyTemplate = "{tableName}//{partitionKey}//{sortKey}"

    val key = S3KeyGenerator.generateS3Key(item, messyTemplate, "orders")

    // Should clean up double slashes
    assertEquals("orders/CUST_456/ORDER_789.json.gz", key)
    assertFalse(key.contains("//"))
  }

  @Test
  fun `generates same key when called multiple times`() {
    val item = CompositeKeyItem("CUST_999", "ORDER_111", 50.00, Instant.now())
    val template = "{tableName}/{partitionKey}/{sortKey}"

    val keys = (1..10).map {
      S3KeyGenerator.generateS3Key(item, template, "orders")
    }

    // All generated keys should be identical (deterministic)
    assertTrue(keys.all { it == keys.first() })
    assertEquals("orders/CUST_999/ORDER_111.json.gz", keys.first())
  }

  @Test
  fun `throws exception if partition key not found`() {
    data class NoKeyItem(
      val userId: String,  // Missing @DynamoDBHashKey
      val name: String
    )

    val item = NoKeyItem("USER_123", "Alice")
    val template = "{tableName}/{partitionKey}"

    val exception = assertThrows(IllegalStateException::class.java) {
      S3KeyGenerator.generateS3Key(item, template, "users")
    }

    assertTrue(exception.message?.contains("Could not find partition key") == true)
  }

  @Test
  fun `extract keys returns correct partition and sort keys`() {
    val singleKeyItem = SingleKeyItem("USER_123", "Alice", Instant.now())
    val (pk1, sk1) = S3KeyGenerator.extractKeys(singleKeyItem)
    assertEquals("USER_123", pk1)
    assertNull(sk1)

    val compositeKeyItem = CompositeKeyItem("CUST_456", "ORDER_789", 99.99, Instant.now())
    val (pk2, sk2) = S3KeyGenerator.extractKeys(compositeKeyItem)
    assertEquals("CUST_456", pk2)
    assertEquals("ORDER_789", sk2)
  }

  @Test
  fun `isKeyField correctly identifies key fields`() {
    val properties = CompositeKeyItem::class.memberProperties

    val customerIdProp = properties.find { it.name == "customerId" }!!
    assertTrue(S3KeyGenerator.isKeyField(customerIdProp))

    val orderIdProp = properties.find { it.name == "orderId" }!!
    assertTrue(S3KeyGenerator.isKeyField(orderIdProp))

    val amountProp = properties.find { it.name == "amount" }!!
    assertFalse(S3KeyGenerator.isKeyField(amountProp))

    val timestampProp = properties.find { it.name == "timestamp" }!!
    assertFalse(S3KeyGenerator.isKeyField(timestampProp))
  }

  @Test
  fun `deterministic keys enable reliable pointer recreation`() {
    // Simulate archival and retrieval flow
    val originalItem = CompositeKeyItem("CUST_100", "ORDER_200", 75.50, Instant.parse("2024-01-15T10:00:00Z"))

    // Archive: Generate S3 key
    val s3Key = S3KeyGenerator.generateS3Key(
      originalItem,
      "{tableName}/{partitionKey}/{sortKey}",
      "orders"
    )

    // Store pointer in DynamoDB (simulated)
    data class Pointer(
      val customerId: String,
      val orderId: String,
      val s3Key: String
    )
    val pointer = Pointer(originalItem.customerId, originalItem.orderId, s3Key)

    // Later: Recreate S3 key from pointer to verify
    val recreatedItem = CompositeKeyItem(
      pointer.customerId,
      pointer.orderId,
      0.0,  // Don't have original amount
      Instant.now()  // Don't have original timestamp
    )

    val recreatedS3Key = S3KeyGenerator.generateS3Key(
      recreatedItem,
      "{tableName}/{partitionKey}/{sortKey}",
      "orders"
    )

    // Keys should match exactly (deterministic)
    assertEquals(s3Key, recreatedS3Key)
    assertEquals("orders/CUST_100/ORDER_200.json.gz", s3Key)
  }
}