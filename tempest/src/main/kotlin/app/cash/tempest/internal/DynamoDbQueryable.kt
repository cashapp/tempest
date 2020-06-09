package app.cash.tempest.internal

import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.Queryable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BETWEEN
import com.amazonaws.services.dynamodbv2.model.Condition
import com.amazonaws.services.dynamodbv2.model.Select.SPECIFIC_ATTRIBUTES

internal class DynamoDbQueryable<K : Any, I : Any>(
  private val keyType: KeyType,
  private val itemType: ItemType,
  private val rawItemType: RawItemType,
  private val dynamoDbMapper: DynamoDBMapper
) : Queryable<K, I> {
  private val tableModel = rawItemType.tableModel

  override fun query(
    startInclusive: K,
    endExclusive: K,
    consistentRead: Boolean,
    asc: Boolean,
    pageSize: Int,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    requireNotNull(
        itemType.primaryIndex.rangeKeyName) { "You can query a table or an index only if it has a composite primary key (partition key and sort key)" }
    val start = keyType.codec.toDb(startInclusive)
    val end = keyType.codec.toDb(endExclusive)
    val startAttributes = tableModel.convert(start)
    val endAttributes = tableModel.convert(end)
    val query = DynamoDBQueryExpression<Any>()
    val (hashKeyName, rangeKeyName) = if (keyType.secondaryIndexName != null) {
      query.withIndexName(keyType.secondaryIndexName)
      val index = requireNotNull(itemType.secondaryIndexes[keyType.secondaryIndexName])
      index.hashKeyName to index.rangeKeyName
    } else {
      itemType.primaryIndex.hashKeyName to itemType.primaryIndex.rangeKeyName
    }
    require(startAttributes[hashKeyName] == endAttributes[hashKeyName])
    val hashKeyValue = tableModel.unconvert(mapOf(hashKeyName to startAttributes[hashKeyName]))
    query.withHashKeyValues(hashKeyValue)
        .withRangeKeyCondition(rangeKeyName,
            Condition()
                .withComparisonOperator(BETWEEN)
                .withAttributeValueList(startAttributes[rangeKeyName], endAttributes[rangeKeyName]))
    query.isScanIndexForward = asc
    query.isConsistentRead = consistentRead
    query.limit = pageSize
    query.withSelect(SPECIFIC_ATTRIBUTES)
    query.projectionExpression = itemType.attributeNames.joinToString(", ")
    if (initialOffset != null) {
      query.exclusiveStartKey = initialOffset.encodeOffset()
    }
    val page = dynamoDbMapper.queryPage(rawItemType.type.java, query)
    val contents = page.results.map { itemType.codec.toApp(it) }
    val offset = page.lastEvaluatedKey?.decodeOffset<K>()
    return Page(contents, offset) as Page<K, I>
  }

  private fun <K : Any> Offset<K>.encodeOffset(): Map<String, AttributeValue> {
    val offsetKey = keyType.codec.toDb(key)
    return tableModel.convert(offsetKey)
  }

  private fun <K : Any> Map<String, AttributeValue>.decodeOffset(): Offset<K> {
    val offsetKeyAttributes = tableModel.unconvert(this)
    val offsetKey = keyType.codec.toApp(offsetKeyAttributes) as K
    return Offset(offsetKey)
  }
}
