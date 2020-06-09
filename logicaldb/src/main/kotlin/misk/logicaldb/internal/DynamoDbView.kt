package misk.logicaldb.internal

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior.CLOBBER
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior.PUT
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression
import misk.logicaldb.View

internal class DynamoDbView<K : Any, I : Any>(
  private val keyCodec: Codec<K, Any>,
  private val itemCodec: Codec<I, Any>,
  private val dynamoDbMapper: DynamoDBMapper
) : View<K, I> {

  override fun load(key: K, consistentReads: DynamoDBMapperConfig.ConsistentReads): I? {
    val keyObject = keyCodec.toDb(key)
    val itemObject = dynamoDbMapper.load(keyObject, consistentReads.config())
    return if (itemObject != null) itemCodec.toApp(itemObject) else null
  }

  override fun save(
    item: I,
    saveExpression: DynamoDBSaveExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val itemObject = itemCodec.toDb(item)
    val saveBehavior = if (ignoreVersionConstraints) CLOBBER else PUT
    dynamoDbMapper.save(itemObject, saveExpression, saveBehavior.config())
  }

  override fun deleteKey(
    key: K,
    deleteExpression: DynamoDBDeleteExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val keyObject = keyCodec.toDb(key)
    deleteInternal(keyObject, deleteExpression, ignoreVersionConstraints)
  }

  override fun delete(
    item: I,
    deleteExpression: DynamoDBDeleteExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val itemObject = itemCodec.toDb(item)
    deleteInternal(itemObject, deleteExpression, ignoreVersionConstraints)
  }

  private fun deleteInternal(
    itemObject: Any,
    deleteExpression: DynamoDBDeleteExpression?,
    ignoreVersionConstraints: Boolean
  ) {
    val saveBehavior = if (ignoreVersionConstraints) CLOBBER else PUT
    dynamoDbMapper.delete(itemObject, deleteExpression, saveBehavior.config())
  }
}
