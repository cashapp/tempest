/*
 * Copyright 2021 Square Inc.
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

package app.cash.tempest2.internal

import app.cash.tempest.internal.Codec
import app.cash.tempest.internal.GetterMethodHandler
import app.cash.tempest.internal.ItemType
import app.cash.tempest.internal.KeyType
import app.cash.tempest.internal.MethodHandler
import app.cash.tempest.internal.ProxyFactory
import app.cash.tempest.internal.RawItemType
import app.cash.tempest.internal.Schema
import app.cash.tempest.internal.declaredMembers
import app.cash.tempest2.AsyncInlineView
import app.cash.tempest2.AsyncLogicalDb
import app.cash.tempest2.AsyncLogicalTable
import app.cash.tempest2.AsyncQueryable
import app.cash.tempest2.AsyncScannable
import app.cash.tempest2.AsyncSecondaryIndex
import app.cash.tempest2.AsyncView
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

internal class AsyncLogicalDbFactory(
  private val dynamoDbEnhancedClient: DynamoDbEnhancedAsyncClient
) : AsyncLogicalTable.Factory {
  private val schema = Schema.create(
    V2StringAttributeValue,
    V2MapAttributeValue.Factory,
    V2ForIndexAnnotation,
    V2AttributeAnnotation,
    V2RawItemTypeFactory()
  )

  fun <DB : AsyncLogicalDb> logicalDb(dbType: KClass<DB>): DB {
    val logicalDb = DynamoDbLogicalDb(
      DynamoDbLogicalDb.MappedTableResourceFactory.simple(dynamoDbEnhancedClient::table),
      schema,
    ).async(dynamoDbEnhancedClient, this)
    val methodHandlers = mutableMapOf<Method, MethodHandler>()
    for (member in dbType.declaredMembers) {
      if (!member.returnType.jvmErasure.isSubclassOf(AsyncLogicalTable::class)) {
        continue
      }
      val tableType = member.returnType.jvmErasure as KClass<AsyncLogicalTable<Any>>
      val tableName = getTableName(member, dbType)
      val table = logicalTable(tableName, tableType)
      methodHandlers[member.javaMethod] = GetterMethodHandler(table)
    }
    return ProxyFactory.create(dbType, methodHandlers.toMap(), logicalDb)
  }

  override fun <T : AsyncLogicalTable<RI>, RI : Any> logicalTable(tableName: String, tableType: KClass<T>): T {
    val rawItemType = schema.addRawItem(tableName, tableType.rawItemType)
    val tableSchema = TableSchema.fromClass(rawItemType.type.java)
    val dynamoDbTable = dynamoDbEnhancedClient.table(rawItemType.tableName, tableSchema)
    val logicalTable =
      object :
        AsyncLogicalTable<RI>,
        AsyncView<RI, RI> by DynamoDbView(
          rawItemType.codec as Codec<RI, Any>,
          rawItemType.codec as Codec<RI, Any>,
          tableSchema,
        ).async(dynamoDbTable),
        AsyncInlineView.Factory by InlineViewFactory(rawItemType, tableSchema, dynamoDbTable),
        AsyncSecondaryIndex.Factory by SecondaryIndexFactory(rawItemType, tableSchema, dynamoDbTable) {
        override fun <T : Any> codec(type: KClass<T>): app.cash.tempest2.Codec<T, RI> = CodecAdapter(schema.codec(type))
      }
    val methodHandlers = mutableMapOf<Method, MethodHandler>()
    for (member in tableType.declaredMembers) {
      val component = when (member.returnType.jvmErasure) {
        AsyncInlineView::class -> {
          val keyType = member.returnType.arguments[0].type?.jvmErasure!!
          val itemType = member.returnType.arguments[1].type?.jvmErasure!!
          logicalTable.inlineView(keyType, itemType)
        }
        AsyncSecondaryIndex::class -> {
          val keyType = member.returnType.arguments[0].type?.jvmErasure!!
          val itemType = member.returnType.arguments[1].type?.jvmErasure!!
          logicalTable.secondaryIndex(keyType, itemType)
        }
        else -> null
      }
      methodHandlers[member.javaMethod] = GetterMethodHandler(component)
    }
    return ProxyFactory.create(tableType, methodHandlers.toMap(), logicalTable)
  }

  inner class InlineViewFactory(
    private val rawItemType: RawItemType,
    private val tableSchema: TableSchema<Any>,
    private val dynamoDbTable: DynamoDbAsyncTable<Any>,
  ) : AsyncInlineView.Factory {

    override fun <K : Any, I : Any> inlineView(
      keyType: KClass<K>,
      itemType: KClass<I>
    ): AsyncInlineView<K, I> {
      val item = schema.addItem(itemType, rawItemType.type)
      val key = schema.addKey(keyType, itemType)
      return object :
        AsyncInlineView<K, I>,
        AsyncView<K, I> by DynamoDbView(
          key.codec as Codec<K, Any>,
          item.codec as Codec<I, Any>,
          tableSchema,
        ).async(dynamoDbTable),
        AsyncQueryable<K, I> by queryable(
          rawItemType,
          item,
          key,
          tableSchema,
          dynamoDbTable,
        ),
        AsyncScannable<K, I> by scannable(
          item,
          key,
          tableSchema,
          dynamoDbTable,
        ) {}
    }
  }

  inner class SecondaryIndexFactory(
    private val rawItemType: RawItemType,
    private val tableSchema: TableSchema<Any>,
    private val dynamoDbTable: DynamoDbAsyncTable<Any>,
  ) : AsyncSecondaryIndex.Factory {

    override fun <K : Any, I : Any> secondaryIndex(
      keyType: KClass<K>,
      itemType: KClass<I>
    ): AsyncSecondaryIndex<K, I> {
      val item = schema.addItem(itemType, rawItemType.type)
      val key = schema.addKey(keyType, itemType)
      return object :
        AsyncSecondaryIndex<K, I>,
        AsyncQueryable<K, I> by queryable(
          rawItemType,
          item,
          key,
          tableSchema,
          dynamoDbTable,
        ),
        AsyncScannable<K, I> by scannable(
          item,
          key,
          tableSchema,
          dynamoDbTable,
        ) {}
    }
  }

  private fun <K : Any, I : Any> queryable(
    rawItemType: RawItemType,
    itemType: ItemType,
    keyType: KeyType,
    tableSchema: TableSchema<Any>,
    dynamoDbTable: DynamoDbAsyncTable<Any>,
  ): AsyncQueryable<K, I> {
    if (keyType.rangeKeyName == null) {
      return UnsupportedAsyncQueryable(rawItemType.type)
    }
    return DynamoDbQueryable(
      keyType.secondaryIndexName,
      itemType.attributeNames,
      keyType.codec as Codec<K, Any>,
      itemType.codec as Codec<I, Any>,
      tableSchema,
    ).async(dynamoDbTable)
  }

  private fun <K : Any, I : Any> scannable(
    itemType: ItemType,
    keyType: KeyType,
    tableSchema: TableSchema<Any>,
    dynamoDbTable: DynamoDbAsyncTable<Any>,
  ): AsyncScannable<K, I> {
    return DynamoDbScannable(
      keyType.secondaryIndexName,
      itemType.attributeNames,
      keyType.codec as Codec<K, Any>,
      itemType.codec as Codec<I, Any>,
      tableSchema,
    ).async(dynamoDbTable)
  }

  private val <T : AsyncLogicalTable<RI>, RI : Any> KClass<T>.rawItemType: KClass<RI>
    get() = supertypes[0].arguments[0].type?.jvmErasure!! as KClass<RI>
}
