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
import app.cash.tempest.SecondaryIndex
import app.cash.tempest.View
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmErasure

internal class LogicalDbFactory(
  private val dynamoDbMapper: DynamoDBMapper,
  private val config: DynamoDBMapperConfig
) {

  private val logicalTableFactory = LogicalTableFactory()
  private val proxyFactory: ProxyFactory = ProxyFactory()
  private val schema: Schema

  init {
    val reflectionCodecFactory = ReflectionCodec.Factory()
    schema = Schema(
        KeyType.Factory(reflectionCodecFactory),
        ItemType.Factory(reflectionCodecFactory),
        RawItemType.Factory(dynamoDbMapper, config)
    )
  }

  fun <DB : LogicalDb> logicalDb(dbType: KClass<DB>): DB {
    val methodHandlers = mutableMapOf<Method, MethodHandler>()
    for (property in dbType.declaredMemberProperties) {
      if (!property.returnType.jvmErasure.isSubclassOf(LogicalTable::class)) {
        continue
      }
      val tableType = property.returnType.jvmErasure as KClass<LogicalTable<Any>>
      val table = logicalTableFactory.logicalTable(tableType)
      methodHandlers[property.javaGetter!!] = GetterMethodHandler(table)
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
    val (hashKeyName, rangeKeyName) =
      if (keyType.secondaryIndexName != null) {
        val index = requireNotNull(itemType.secondaryIndexes[keyType.secondaryIndexName]) {
          "Could not find secondary index ${keyType.secondaryIndexName} in ${itemType.rawItemType}"
        }
        index.hashKeyName to index.rangeKeyName
      } else {
        val index = itemType.primaryIndex
        if (index.rangeKeyName == null) {
          return UnsupportedQueryable(rawItemType.type)
        }
        index.hashKeyName to index.rangeKeyName
      }
    return DynamoDbQueryable(
      hashKeyName,
      rangeKeyName,
      itemType.attributeNames,
      keyType.codec as Codec<K, Any>,
      itemType.codec as Codec<I, Any>,
      rawItemType.type,
      rawItemType.tableModel,
      dynamoDbMapper)
  }

  inner class LogicalTableFactory : LogicalTable.Factory {

    override fun <T : LogicalTable<RI>, RI : Any> logicalTable(tableType: KClass<T>): T {
      val rawItemType = schema.addRawItem(tableType.rawItemType)
      val codec = rawItemType.codec as Codec<RI, Any>
      val view = DynamoDbView(
          codec,
          codec,
          dynamoDbMapper)
      val inlineViewFactory = InlineViewFactory(rawItemType)
      val secondaryIndexFactory = SecondaryIndexFactory(rawItemType)
      val logicalTable =
          object : LogicalTable<RI>,
              View<RI, RI> by view,
              InlineView.Factory by inlineViewFactory,
              SecondaryIndex.Factory by secondaryIndexFactory {}
      val methodHandlers = mutableMapOf<Method, MethodHandler>()
      for (property in tableType.declaredMemberProperties) {
        val component = when (property.returnType.jvmErasure) {
          InlineView::class -> {
            val keyType = property.returnType.arguments[0].type?.jvmErasure!!
            val itemType = property.returnType.arguments[1].type?.jvmErasure!!
            inlineViewFactory.inlineView(keyType, itemType)
          }
          SecondaryIndex::class -> {
            val keyType = property.returnType.arguments[0].type?.jvmErasure!!
            val itemType = property.returnType.arguments[1].type?.jvmErasure!!
            secondaryIndexFactory.secondaryIndex(keyType, itemType)
          }
          else -> null
        }
        methodHandlers[property.javaGetter!!] = GetterMethodHandler(component)
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
          dynamoDbMapper)
      val queryable = queryable<K, I>(
          rawItemType,
          item,
          key)
      return object : InlineView<K, I>, View<K, I> by view, Queryable<K, I> by queryable {}
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
        key)
      return object : SecondaryIndex<K, I>, Queryable<K, I> by queryable {}
    }
  }

  companion object {
    val <T : LogicalTable<RI>, RI : Any> KClass<T>.rawItemType: KClass<RI>
      get() = supertypes[0].arguments[0].type?.jvmErasure!! as KClass<RI>
  }
}
