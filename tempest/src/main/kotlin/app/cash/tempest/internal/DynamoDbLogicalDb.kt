package app.cash.tempest.internal

import app.cash.tempest.BatchWriteResult
import app.cash.tempest.BatchWriteSet
import app.cash.tempest.ItemSet
import app.cash.tempest.KeySet
import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import app.cash.tempest.TransactionWriteSet
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads
import com.amazonaws.services.dynamodbv2.datamodeling.TransactionLoadRequest
import com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException

internal class DynamoDbLogicalDb(
  private val dynamoDbMapper: DynamoDBMapper,
  private val schema: Schema,
  logicalTableFactory: LogicalTable.Factory
) : LogicalDb, LogicalTable.Factory by logicalTableFactory {

  override fun batchLoad(keys: KeySet, consistentReads: ConsistentReads): ItemSet {
    val keysToLoad = mutableListOf<Any>()
    val itemTypes = mutableMapOf<RawItemType.RawItemKey, ItemType>()
    val rawItemTypes = mutableMapOf<String, RawItemType>()
    for (key in keys) {
      val keyRawItem = key.encodeAsKey()
      val rawItemType = key.expectedRawItemType()
      keysToLoad.add(keyRawItem)
      itemTypes[rawItemType.key(keyRawItem)] = key.expectedItemType()
      rawItemTypes[rawItemType.tableName] = rawItemType
    }
    val loadedRawItems = dynamoDbMapper.batchLoad(keysToLoad, consistentReads.config())
    val results = mutableSetOf<Any>()
    for ((tableName, rawItems) in loadedRawItems) {
      val rawItemType =
          requireNotNull(rawItemTypes[tableName]) { "Unexpected table name $tableName" }
      for (rawItem in rawItems) {
        if (rawItem == null) continue
        val itemType = itemTypes[rawItemType.key(rawItem)]!!
        val decoded = itemType.codec.toApp(rawItem)
        results.add(decoded)
      }
    }
    return ItemSet(results)
  }

  override fun batchWrite(writeSet: BatchWriteSet): BatchWriteResult {
    val failedBatches = dynamoDbMapper.batchWrite(
        writeSet.itemsToClobber.encodeAsItems(),
        writeSet.keysToDelete.encodeAsKeys())
    return BatchWriteResult(failedBatches)
  }

  override fun transactionLoad(keys: KeySet): ItemSet {
    val request = TransactionLoadRequest()
    val itemTypes = mutableListOf<ItemType>()
    for (key in keys) {
      request.addLoad(key.encodeAsKey())
      itemTypes.add(key.expectedItemType())
    }
    val loadedRawItems = dynamoDbMapper.transactionLoad(request)
    val results = mutableSetOf<Any>()
    for ((rawItem, itemType) in loadedRawItems.zip(itemTypes)) {
      if (rawItem == null) continue
      val decoded = itemType.codec.toApp(rawItem)
      results.add(decoded)
    }
    return ItemSet(results)
  }

  override fun transactionWrite(writeSet: TransactionWriteSet) {
    val writeRequest = TransactionWriteRequest()
    for (itemToSave in writeSet.itemsToSave) {
      val expression = writeSet.writeExpressions[itemToSave]
      writeRequest.addUpdate(itemToSave.encodeAsItem(), expression)
    }
    for (keyToDelete in writeSet.keysToDelete) {
      val expression = writeSet.writeExpressions[keyToDelete]
      writeRequest.addDelete(keyToDelete.encodeAsKey(), expression)
    }
    for (keyToCheck in writeSet.keysToCheck) {
      val expression = writeSet.writeExpressions[keyToCheck]
      writeRequest.addConditionCheck(keyToCheck.encodeAsKey(), expression)
    }
    if (writeSet.idempotencyToken != null) {
      writeRequest.withIdempotencyToken(writeSet.idempotencyToken)
    }
    try {
      dynamoDbMapper.transactionWrite(writeRequest)
    } catch (e: TransactionCanceledException) {
      val reasons = writeSet.describeOperations().zip(e.cancellationReasons)
      throw IllegalStateException("Failed to transaction write: $reasons", e)
    }
  }

  private fun Any.expectedRawItemType(): RawItemType {
    return requireNotNull(schema.resolveEnclosingRawItemType(
        this::class)) { "Cannot find a dynamodb table for ${this::class}" }
  }

  private fun Any.expectedItemType(): ItemType {
    return requireNotNull(schema.resolveEnclosingItemType(
        this::class)) { "Cannot find an item type for ${this::class}" }
  }

  private fun Any.encodeAsKey(): Any {
    val type = this::class
    // Fallback to item codec because keys can be extracted from items.
    val codec = schema.getKey(type)?.codec ?: schema.getItem(type)?.codec
    requireNotNull(codec) { "Failed to encode $type" }
    return codec.toDb(this)
  }

  private fun Any.encodeAsItem(): Any {
    val type = this::class
    val codec = schema.getItem(type)?.codec
    requireNotNull(codec) { "Failed to encode $type" }
    return codec.toDb(this)
  }

  private fun Iterable<Any>.encodeAsKeys(): Iterable<Any> {
    return map { it.encodeAsKey() }
  }

  private fun Iterable<Any>.encodeAsItems(): Iterable<Any> {
    return map { it.encodeAsItem() }
  }

  private fun TransactionWriteSet.describeOperations(): List<String> {
    val descriptions = mutableListOf<String>()
    for (itemToSave in itemsToSave) {
      descriptions.add("Save $itemToSave")
    }
    for (keyToDelete in keysToDelete) {
      descriptions.add("Delete $keyToDelete")
    }
    for (keyToCheck in keysToCheck) {
      descriptions.add("Check $keyToCheck")
    }
    return descriptions.toList()
  }
}
