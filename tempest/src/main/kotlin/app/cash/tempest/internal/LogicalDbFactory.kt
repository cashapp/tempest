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
      val queryable = DynamoDbQueryable<K, I>(
          key,
          item,
          rawItemType,
          dynamoDbMapper)
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
      val queryable = DynamoDbQueryable<K, I>(
          key,
          item,
          rawItemType,
          dynamoDbMapper)
      return object : SecondaryIndex<K, I>, Queryable<K, I> by queryable {}
    }
  }

  companion object {
    val <T : LogicalTable<RI>, RI : Any> KClass<T>.rawItemType: KClass<RI>
      get() = supertypes[0].arguments[0].type?.jvmErasure!! as KClass<RI>
  }
}
