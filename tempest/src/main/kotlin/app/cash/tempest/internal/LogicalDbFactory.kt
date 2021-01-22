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

package app.cash.tempest.internal

import app.cash.tempest.InlineView
import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import app.cash.tempest.Queryable
import app.cash.tempest.Scannable
import app.cash.tempest.SecondaryIndex
import app.cash.tempest.View
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

internal class LogicalDbFactory(
  private val dynamoDbMapper: DynamoDBMapper,
  private val config: DynamoDBMapperConfig
) {

  private val logicalTableFactory = LogicalTableFactory()
  private val proxyFactory: ProxyFactory = ProxyFactory()
  private val schema = Schema.create(
    V1StringAttributeValue,
    V1MapAttributeValue.Factory(dynamoDbMapper),
    V1ForIndexAnnotation,
    V1AttributeAnnotation,
    V1RawItemTypeFactory(dynamoDbMapper)
  )

  fun <DB : LogicalDb> logicalDb(dbType: KClass<DB>): DB {
    val methodHandlers = mutableMapOf<Method, MethodHandler>()
    for (member in dbType.declaredMembers) {
      if (!member.returnType.jvmErasure.isSubclassOf(LogicalTable::class)) {
        continue
      }
      val tableType = member.returnType.jvmErasure as KClass<LogicalTable<Any>>
      val table = logicalTableFactory.logicalTable(tableType)
      methodHandlers[member.javaMethod] = GetterMethodHandler(table)
    }
    val logicalDb = DynamoDbLogicalDb(
      dynamoDbMapper,
      schema,
      logicalTableFactory
    )
    return proxyFactory.create(dbType, methodHandlers.toMap(), logicalDb)
  }

  private fun <K : Any, I : Any> queryable(
    rawItemType: RawItemType,
    itemType: ItemType,
    keyType: KeyType
  ): Queryable<K, I> {
    if (keyType.rangeKeyName == null) {
      return UnsupportedQueryable(rawItemType.type)
    }
    return DynamoDbQueryable(
      keyType.hashKeyName,
      keyType.rangeKeyName!!,
      keyType.secondaryIndexName,
      itemType.attributeNames,
      keyType.codec as Codec<K, Any>,
      itemType.codec as Codec<I, Any>,
      rawItemType.type,
      dynamoDbMapper.getTableModel(rawItemType.type.java) as DynamoDBMapperTableModel<Any>,
      dynamoDbMapper
    )
  }

  private fun <K : Any, I : Any> scannable(
    rawItemType: RawItemType,
    itemType: ItemType,
    keyType: KeyType
  ): Scannable<K, I> {
    return DynamoDBScannable(
      keyType.secondaryIndexName,
      itemType.attributeNames,
      keyType.codec as Codec<K, Any>,
      itemType.codec as Codec<I, Any>,
      rawItemType.type,
      dynamoDbMapper.getTableModel(rawItemType.type.java) as DynamoDBMapperTableModel<Any>,
      dynamoDbMapper
    )
  }

  inner class LogicalTableFactory : LogicalTable.Factory {

    override fun <T : LogicalTable<RI>, RI : Any> logicalTable(tableType: KClass<T>): T {
      val tableName = config.tableNameResolver.getTableName(tableType.rawItemType.java, config)
      val rawItemType = schema.addRawItem(tableName, tableType.rawItemType)
      val codec = rawItemType.codec as Codec<RI, Any>
      val view = DynamoDbView(
        codec,
        codec,
        dynamoDbMapper
      )
      val inlineViewFactory = InlineViewFactory(rawItemType)
      val secondaryIndexFactory = SecondaryIndexFactory(rawItemType)
      val logicalTable =
        object : LogicalTable<RI>,
          View<RI, RI> by view,
          InlineView.Factory by inlineViewFactory,
          SecondaryIndex.Factory by secondaryIndexFactory {
          override fun <T : Any> codec(type: KClass<T>): app.cash.tempest.Codec<T, RI> = CodecAdapter(schema.codec(type))
        }
      val methodHandlers = mutableMapOf<Method, MethodHandler>()
      for (member in tableType.declaredMembers) {
        val component = when (member.returnType.jvmErasure) {
          InlineView::class -> {
            val keyType = member.returnType.arguments[0].type?.jvmErasure!!
            val itemType = member.returnType.arguments[1].type?.jvmErasure!!
            inlineViewFactory.inlineView(keyType, itemType)
          }
          SecondaryIndex::class -> {
            val keyType = member.returnType.arguments[0].type?.jvmErasure!!
            val itemType = member.returnType.arguments[1].type?.jvmErasure!!
            secondaryIndexFactory.secondaryIndex(keyType, itemType)
          }
          else -> null
        }
        methodHandlers[member.javaMethod] = GetterMethodHandler(component)
      }
      return proxyFactory.create(tableType, methodHandlers.toMap(), logicalTable)
    }
  }

  inner class InlineViewFactory(
    private val rawItemType: RawItemType
  ) : InlineView.Factory {

    override fun <K : Any, I : Any> inlineView(
      keyType: KClass<K>,
      itemType: KClass<I>
    ): InlineView<K, I> {
      val item = schema.addItem(itemType, rawItemType.type)
      val key = schema.addKey(keyType, itemType)
      val view = DynamoDbView(
        key.codec as Codec<K, Any>,
        item.codec as Codec<I, Any>,
        dynamoDbMapper
      )
      val queryable = queryable<K, I>(
        rawItemType,
        item,
        key
      )
      val scannable = scannable<K, I>(
        rawItemType,
        item,
        key
      )
      return object : InlineView<K, I>, View<K, I> by view, Queryable<K, I> by queryable,
        Scannable<K, I> by scannable {}
    }
  }

  inner class SecondaryIndexFactory(
    private val rawItemType: RawItemType
  ) : SecondaryIndex.Factory {

    override fun <K : Any, I : Any> secondaryIndex(
      keyType: KClass<K>,
      itemType: KClass<I>
    ): SecondaryIndex<K, I> {
      val item = schema.addItem(itemType, rawItemType.type)
      val key = schema.addKey(keyType, itemType)
      val queryable = queryable<K, I>(
        rawItemType,
        item,
        key
      )
      val scannable = scannable<K, I>(
        rawItemType,
        item,
        key
      )
      return object : SecondaryIndex<K, I>, Queryable<K, I> by queryable,
        Scannable<K, I> by scannable {}
    }
  }

  private class CodecAdapter<A : Any, D : Any>(
    private val internal: Codec<A, D>
  ) : app.cash.tempest.Codec<A, D> {
    override fun toDb(appItem: A): D = internal.toDb(appItem)
    override fun toApp(dbItem: D): A = internal.toApp(dbItem)
  }

  companion object {
    val <T : LogicalTable<RI>, RI : Any> KClass<T>.rawItemType: KClass<RI>
      get() = supertypes[0].arguments[0].type?.jvmErasure!! as KClass<RI>
  }
}
