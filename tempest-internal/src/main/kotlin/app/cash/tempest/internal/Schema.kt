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

package app.cash.tempest.internal

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.withNullability

class Schema private constructor(
  private val keyTypeFactory: KeyType.Factory,
  private val itemTypeFactory: ItemType.Factory,
  private val rawItemTypeFactory: RawItemType.Factory
) {
  private val keyTypes = mutableMapOf<KClass<*>, KeyType>()
  private val itemTypes = mutableMapOf<KClass<*>, ItemType>()
  private val rawItemTypes = mutableMapOf<KClass<*>, RawItemType>()

  fun getKey(type: KClass<*>): KeyType? = keyTypes[type]

  fun getItem(type: KClass<*>): ItemType? = itemTypes[type]

  fun getRawItem(type: KClass<*>): RawItemType? = rawItemTypes[type]

  fun resolveEnclosingItemType(type: KClass<*>): ItemType? {
    val logicalType = getLogicalType(type) ?: return null
    return when (logicalType) {
      is KeyType -> getItem(logicalType.itemType)
      is ItemType -> logicalType
      else -> null
    }
  }

  fun resolveEnclosingRawItemType(type: KClass<*>): RawItemType? {
    val logicalType = getLogicalType(type) ?: return null
    return when (logicalType) {
      is KeyType -> getRawItem(getItem(logicalType.itemType)!!.rawItemType)
      is ItemType -> getRawItem(logicalType.rawItemType)
      is RawItemType -> logicalType
    }
  }

  private fun getLogicalType(type: KClass<*>): LogicalType? = getKey(type)
    ?: getItem(type) ?: getRawItem(type)

  fun addKey(keyType: KClass<*>, itemType: KClass<*>): KeyType {
    val existingKeyType = keyTypes.getOrPut(keyType) {
      val itemType = requireNotNull(getItem(itemType))
      keyTypeFactory.create(keyType, itemType, requireNotNull(getRawItem(itemType.rawItemType)))
    }
    require(existingKeyType.itemType == itemType) {
      "Cannot bind $keyType to $itemType because it is already bound to ${existingKeyType.itemType}"
    }
    return existingKeyType
  }

  fun addItem(itemType: KClass<*>, rawItemType: KClass<*>): ItemType {
    val existingItemType = itemTypes.getOrPut(itemType) {
      itemTypeFactory.create(itemType, requireNotNull(getRawItem(rawItemType)))
    }
    require(existingItemType.rawItemType == rawItemType) {
      "Cannot bind $itemType to $rawItemType because it is already bound to ${existingItemType.rawItemType}"
    }
    return existingItemType
  }

  fun addRawItem(tableName: String, rawItemType: KClass<*>): RawItemType {
    return rawItemTypes.getOrPut(rawItemType) {
      rawItemTypeFactory.create(tableName, rawItemType)
    }
  }

  fun <T : Any, RI : Any> codec(type: KClass<T>): Codec<T, RI> {
    val keyType: KeyType? = keyTypes[type]
    if (keyType != null) return keyType.codec as Codec<T, RI>

    val itemType = itemTypes[type]
    if (itemType != null) return itemType.codec as Codec<T, RI>

    throw IllegalArgumentException(
      "unexpected type $type not in ${keyTypes.keys} or ${itemTypes.keys}"
    )
  }

  companion object {
    fun create(
      stringAttributeValue: StringAttributeValue<*>,
      mapAttributeValueFactory: MapAttributeValue.Factory,
      forIndexAnnotation: ForIndexAnnotation<*>,
      attributeAnnotation: AttributeAnnotation<*>,
      rawItemTypeFactory: RawItemType.Factory
    ): Schema {
      val reflectionCodecFactory = ReflectionCodec.Factory(stringAttributeValue, mapAttributeValueFactory)
      return Schema(
        KeyType.Factory(reflectionCodecFactory, forIndexAnnotation, attributeAnnotation),
        ItemType.Factory(reflectionCodecFactory, attributeAnnotation),
        rawItemTypeFactory
      )
    }
  }
}

sealed class LogicalType {
  abstract val codec: Codec<Any, Any>
}

data class KeyType(
  override val codec: Codec<Any, Any>,
  val type: KClass<*>,
  val itemType: KClass<*>,
  val attributeNames: Set<String>,
  val hashKeyName: String,
  val rangeKeyName: String?,
  val secondaryIndexName: String?
) : LogicalType() {

  class Factory internal constructor(
    private val codecFactory: ReflectionCodec.Factory,
    private val forIndexAnnotation: ForIndexAnnotation<*>,
    private val attributeAnnotation: AttributeAnnotation<*>
  ) {
    fun create(keyType: KClass<*>, itemType: ItemType, rawItemType: RawItemType): KeyType {
      require(keyType.constructors.isNotEmpty()) { "$keyType must have a constructor" }
      val constructorParameters = keyType.defaultConstructorParameters
      val attributeNames = mutableSetOf<String>()
      for (property in keyType.memberProperties) {
        if (property.shouldIgnore) {
          continue
        }
        val attribute = requireNotNull(
          itemType.attributes[property.name]
        ) {
          "Expect ${property.name}, required by $keyType, to be declared in ${itemType.type}. But found ${itemType.attributes.keys}." +
            " Use @Transient to exclude it."
        }
        val expectedReturnType =
          requireNotNull(itemType.attributes[property.name]?.returnType).withNullability(false)
        val actualReturnType = property.returnType.withNullability(false)
        require(actualReturnType == expectedReturnType) { "Expect the return type of $keyType.${property.name} to be $expectedReturnType but was $actualReturnType" }
        require(
          !attributeAnnotation.hasAttributeAnnotation(
            property,
            constructorParameters
          )
        ) { "Please move Attribute annotation from $keyType.${property.name} to ${itemType.type}.${property.name}" }
        attributeNames.addAll(attribute.names)
      }
      for (keyAttribute in itemType.keyAttributes(itemType.primaryIndex)) {
        require(attributeNames.containsAll(keyAttribute.names)) { "Expect $keyType to have property ${keyAttribute.propertyName}" }
      }
      val secondaryIndexName = forIndexAnnotation.secondaryIndexName(keyType)
      val (hashKeyName, rangeKeyName) = if (secondaryIndexName != null) {
        val secondaryIndex =
          requireNotNull(itemType.secondaryIndexes[secondaryIndexName]) { "Expect ${itemType.rawItemType} to have secondary index $secondaryIndexName" }
        for (keyAttribute in itemType.keyAttributes(secondaryIndex)) {
          require(attributeNames.containsAll(keyAttribute.names)) { "Expect $keyType to have property ${keyAttribute.propertyName}" }
        }
        secondaryIndex.hashKeyName to secondaryIndex.rangeKeyName
      } else {
        val primaryIndex = itemType.primaryIndex
        primaryIndex.hashKeyName to primaryIndex.rangeKeyName
      }
      return KeyType(
        codecFactory.create(keyType, itemType.attributes, rawItemType),
        keyType,
        itemType.type,
        attributeNames.toSet(),
        hashKeyName,
        rangeKeyName,
        secondaryIndexName
      )
    }
  }
}

data class ItemType(
  override val codec: Codec<Any, Any>,
  val type: KClass<*>,
  val rawItemType: KClass<*>,
  val attributes: Map<String, Attribute>,
  val primaryIndex: PrimaryIndex,
  val secondaryIndexes: Map<String, SecondaryIndex>
) : LogicalType() {

  val attributeNames: Set<String>
    get() = attributes.values.flatMap { it.names }.toSet()

  fun keyAttributes(index: Index): Set<Attribute> {
    val keyAttributes = mutableSetOf<Attribute>()
    for ((_, attribute) in attributes) {
      for (attributeName in attribute.names) {
        if (attributeName == index.hashKeyName || attributeName == index.rangeKeyName) {
          keyAttributes.add(attribute)
        }
      }
    }
    return keyAttributes
  }

  data class Attribute(
    val propertyName: String,
    val names: Set<String>,
    val prefix: String,
    val returnType: KType
  )

  interface Index {
    val hashKeyName: String
    val rangeKeyName: String?
  }

  data class PrimaryIndex(override val hashKeyName: String, override val rangeKeyName: String?) : Index

  data class SecondaryIndex(val name: String, override val hashKeyName: String, override val rangeKeyName: String) : Index

  class Factory internal constructor(
    private val codecFactory: ReflectionCodec.Factory,
    private val attributeAnnotation: AttributeAnnotation<*>
  ) {

    fun create(itemType: KClass<*>, rawItemType: RawItemType): ItemType {
      require(itemType.constructors.isNotEmpty()) { "$itemType must have a constructor" }
      val primaryIndex = PrimaryIndex(rawItemType.hashKeyName, rawItemType.rangeKeyName)
      val attributes = findAttributes(itemType, rawItemType, primaryIndex)
      return ItemType(
        codecFactory.create(itemType, attributes, rawItemType),
        itemType,
        rawItemType.type,
        attributes,
        primaryIndex,
        rawItemType.secondaryIndexes
      )
    }

    private fun findAttributes(
      itemType: KClass<*>,
      rawItemType: RawItemType,
      primaryIndex: PrimaryIndex
    ): Map<String, Attribute> {
      val attributes = mutableMapOf<String, Attribute>()
      val constructorParameters: Map<String, KParameter> = itemType.defaultConstructorParameters
      for (property in itemType.memberProperties) {
        val attribute =
          createAttribute(property, constructorParameters, rawItemType, itemType) ?: continue
        attributes[property.name] = attribute
      }
      val attributesByName =
        attributes.values.flatMap { attribute -> attribute.names.map { it to attribute } }.toMap()
      require(attributesByName.contains(primaryIndex.hashKeyName)) {
        "Expect $itemType to map to ${rawItemType.type}'s hash key ${primaryIndex.hashKeyName}"
      }
      if (primaryIndex.rangeKeyName != null) {
        val rangeKeyAttribute = attributesByName[primaryIndex.rangeKeyName]
        requireNotNull(rangeKeyAttribute) {
          "Expect $itemType to map to ${rawItemType.type}'s range key ${primaryIndex.rangeKeyName}"
        }
        require(rangeKeyAttribute.prefix.isNotEmpty()) {
          "Expect $itemType.${primaryIndex.rangeKeyName} to be annotated with a prefix"
        }
      }
      return attributes.toMap()
    }

    private fun createAttribute(
      property: KProperty<*>,
      constructorParameters: Map<String, KParameter>,
      rawItemType: RawItemType,
      itemType: KClass<*>
    ): Attribute? {
      if (property.shouldIgnore) {
        return null
      }
      val (expectedRawItemAttributes, prefix) = attributeAnnotation.attributeMetadata(
        property,
        constructorParameters
      )
      for (expectedAttribute in expectedRawItemAttributes) {
        require(rawItemType.propertyNames.contains(expectedAttribute)) {
          "Expect $expectedAttribute, required by $itemType, to be declared in " +
            "${rawItemType.type}. But found ${rawItemType.propertyNames}. Use @Transient to exclude it. " +
            "You might see this error message if the property name starts with `is`. " +
            "See: https://github.com/cashapp/tempest/issues/53."
        }
      }
      return Attribute(property.name, expectedRawItemAttributes, prefix, property.returnType)
    }
  }
}

data class RawItemType(
  val type: KClass<Any>,
  val tableName: String,
  val hashKeyName: String,
  val rangeKeyName: String?,
  val propertyNames: List<String>,
  val secondaryIndexes: Map<String, ItemType.SecondaryIndex>
) : LogicalType() {

  override val codec: Codec<Any, Any> = IdentityCodec

  interface Factory {
    fun create(tableName: String, rawItemType: KClass<*>): RawItemType
  }
}
