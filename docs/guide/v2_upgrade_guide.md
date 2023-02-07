# Migration Guide from Tempest v1 to Tempest v2

This guide will explain some items that need to be changed when upgrading from Tempest 1 to Tempest 2

## Dependencies

The first change is to swap the dependency from v1 to v2.

### Depenencies.kt
```diff
- val tempest = "app.cash.tempest:tempest:{dependencies.tempestVersion}"
+ val tempest2 = "app.cash.tempest:tempest2:{dependencies.tempestVersion}"
```

### build.gradle.kts
```diff
- implementation(Dependencies.tempest)
+ implementation(Dependencies.tempest2)
```

## Import Changes

Many of the classes and objects imported from a `tempest` or `aws` package will likely be found by just adding a **2** to the import path.

```diff
- import app.cash.tempest.BeginsWith
+ import app.cash.tempest2.BeginsWith
```

Though some of your imports may have moved into Amazons new package structure

```diff
- import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
+ import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
```

## Logical DB Upgrades

One of the largest changes will be to your `LogicalDB` and the class used as your `LogicalTable<T>` type.

### TableName Annotation

In Tempest v1 your table would likely have been annotated with an `@DynamoDBTable` annotation. This is no longer on the table definition class, but has been moved to an annotation on the member variable inside the LogicalDb interface.

Additionally, that annotation needs to be replaced with an `DynamoDbBean` annotation.

#### Old
```kotlin
interface DyDatabase : LogicalDb {
  val table: DyTable
}

interface DyTable : LogicalTable<DyItem> {
  // View and Index member variables
}

@DynamoDBTable(tableName = TABLE_NAME)
class DyItem {
  // Attribute Definitions
}
```

#### New
```kotlin
interface DyDatabase : LogicalDb {
  @TableName(TABLE_NAME)
  val table: DyTable
}

interface DyTable : LogicalTable<DyItem> {
  // View and Index member variables
}

@DynamoDbBean
class DyItem {
  // Attribute Definitions
}
```

### Hash Key Annotation

`@DynamoDBHashKey` has been replaced by `@get:DynamoDbPartitionKey`

```diff
- @DynamoDBHashKey
+ @get:DynamoDbPartitionKey
  var partition_key: String? = null
```

### Range Key Annotation

`@DynamoDBRangeKey` has been replaced by `@get:DynamoDbSortKey`

```diff
- @DynamoDBRangeKey
+ @get:DynamoDbSortKey
  var sort_key: String? = null
```

### DynamoDBAttribute Annotation

The `@DynamoDBAttribute` is no longer needed on class member variables

```diff
- @DynamoDBAttribute
  var description: String? = null
```

### Index on Hash Keys

`@DynamoDBIndexHashKey` has been replaced by `@get:DynamoDbSecondaryPartitionKey`

```diff
- @DynamoDBIndexHashKey(globalSecondaryIndexName = ENTITY_TYPE_INDEX)
+ @get:DynamoDbSecondaryPartitionKey(indexNames = [ENTITY_TYPE_INDEX])
  var gsi_pk: String? = null
```

### Index on Range Keys

`@DynamoDBIndexRangeKey` has been replaced by `@get:DynamoDbSecondarySortKey`

```diff
- @DynamoDBIndexRangeKey(globalSecondaryIndexName = ENTITY_TYPE_INDEX)
+ @get:DynamoDbSecondarySortKey(indexNames = [ENTITY_TYPE_INDEX])
  var gsi_sk: String? = null
```

### Version Attribute

`@DynamoDBVersionAttribute` has been replaced by `@get:DynamoDbVersionAttribute`

```diff
- @DynamoDBVersionAttribute
+ @get:DynamoDbVersionAttribute
  var version: Long? = null
```

### Type Conversion Annotation

`@DynamoDBTypeConverted` has been replaced by `@get:DynamoDbConvertedBy`

```diff
- @DynamoDBTypeConverted(converter = InstantTypeConverter::class)
+ @get:DynamoDbConvertedBy(InstantAttributeConverter::class)
  var expires_at: Instant? = null
```

### Type Conversion Interface

The `DynamoDBTypeConverter<DBType, Mine>` interface has been replaced by an `AttributeConverter<Mine>` interface.

Instead of having two methods
 * `fun convert(mine: Mine): DBType`
 * `fun unconvert(dbType: DbType): Mine`

There are now four methods
 * `fun transformFrom(mine: Mine): AttributeValue`
    * essentially the same as convert
 * `fun transformTo(input: AttributeValue): Mine`
   * essentiall the same as unconvert
 * `fun type(): EnhancedType<Mine>`
   * Allows the Enhanced Dynamo SDK to avoid Type Erasure
 * `fun attributeValueType(): AttributeValueType`
   * Tells the SDK which value to expect from `transformFrom`

--- 

Included below is an example of the transformation from a `DynamoDBTypeConverter<Long, Instant>` to `AttributeConverter<Instant>`

#### Old

```kotlin
internal class InstantTypeEpochConverter : DynamoDBTypeConverter<Long, Instant> {
  override fun unconvert(epochSeconds: Long): Instant {
    return Instant.ofEpochSecond(epochSeconds)
  }

  override fun convert(instant: Instant): Long {
    return instant.epochSecond
  }
}
```

#### New

```kotlin
internal class InstantTypeEpochConverter : AttributeConverter<Instant> {
  override fun transformFrom(input: Instant): AttributeValue {
    val timeLongAsString = input.epochSecond.toString()
    return AttributeValue.builder()
      .n(timeLongAsString)
      .build()
  }

  override fun transformTo(input: AttributeValue): Instant {
    val timeLong = input.n().toLong()
    return Instant.ofEpochSecond(timeLong)
  }

  override fun type(): EnhancedType<Instant> {
    return EnhancedType.of(Instant::class.java)
  }

  override fun attributeValueType(): AttributeValueType {
    return AttributeValueType.N
  }
}
```

## Creating your LogicalDb

Previously you could create your `LogicalDb` object using an `AmazonDynamoDB` object. However, the new `LogicalDb` objects require `DynamoDbEnhancedClient` objects, which themselves can be created using the new base `DynamoDbClient` object.

### New Creation Semantics
```kotlin
fun createClient(
  awsRegionName: String, 
  awsCredentialsProvider: AwsCredentialsProvider,
) : DynamoDbClient = 
  DynamoDbClient.builder()
    .region(Region.of(awsRegionName))
    .credentialsProvider(awsCredentialsProvider)
    .build()

fun createEnhancedClient(dynamoDbClient: DynamoDbClient): DynamoDbEnhancedClient =
  DynamoDbEnhancedClient.builder()
    .dynamoDbClient(dynamoDbClient)
    .build()

fun createLogicalDb(dynamoDbEnhancedClient: DynamoDbEnhancedClient): DyDatabase =
  LogicalDb<DyDatabase>(dynamoDbEnhancedClient)
```

## API Type changes

### Consistent Read Enum

When doing a `load` from an `InlineView` object in Tempest 1 the consistent read was specified via an enum. In Tempest 2 the option is now a boolean specifying to use consistent reads or not.

```diff
val key = ...
val entity = dyDatabase.dyTable.dyEntitiy.load(
  key = key,
- consistentReads = DynamoDBMapperConfig.ConsistentReads.CONSISTENT
+ consistentReads = true
)
```

## Changes to Misk

If you are using [Misk](https://github.com/cashapp/misk) you will need to update your dependencies and DI configuration

### Dependencies

```diff
-val miskAwsDynamodb = "com.squareup.misk:misk-aws-dynamodb:VERSION"
-val miskAwsDynamodbTesting = "com.squareup.misk:misk-aws-dynamodb-testing:VERSION"
+val miskAws2Dynamodb = "com.squareup.misk:misk-aws2-dynamodb:VERSION"
+val miskAws2DynamodbTesting = "com.squareup.misk:misk-aws2-dynamodb-testing:VERSION"
```

### DynamoDbModule

Changes to the configuration of the `RealDynamoDbModule` are due to changes in the AWS SDK v2 for the Dynamo Client. An example of the changes is below

```diff
-import misk.dynamodb.RealDynamoDbModule
-import com.amazonaws.ClientConfiguration
-install(
-  RealDynamoDbModule(
-    ClientConfiguration()
-      .withMaxErrorRetry(DYNAMO_CLIENT_MAX_ERROR_RETRIES)
-      // Set a timeout per retry.
-      .withRequestTimeout(DYNAMO_REQUEST_TIMEOUT_MILLIS)
-      .withRetryPolicy(
-        PredefinedRetryPolicies
-          .getDynamoDBDefaultRetryPolicyWithCustomMaxRetries(DYNAMO_CLIENT_MAX_ERROR_RETRIES)
-      ),
-    DyItem::class
-  )
-)
+import misk.aws2.dynamodb.RealDynamoDbModule
+import misk.aws2.dynamodb.RequiredDynamoDbTable
+import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
+install(
+  RealDynamoDbModule(
+    ClientOverrideConfiguration.builder()
+      .retryPolicy(
+        RetryPolicy.defaultRetryPolicy().copy {
+          it.numRetries(
+            DYNAMO_CLIENT_MAX_ERROR_RETRIES
+          )
+        }
+      )
+      .apiCallAttemptTimeout(Duration.ofMillis(DYNAMO_REQUEST_TIMEOUT_MILLIS.toLong()))
+      .apiCallTimeout(Duration.ofMillis(DYNAMO_CLIENT_EXECUTION_TIMEOUT_MILLIS.toLong()))
+      .build(),
+    listOf(
+      Constants.DyTable,
+    ).map { RequiredDynamoDbTable(it) }
+  )
+)
```
