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
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

interface StringAttributeValue<T : Any> {
  fun toAttributeValue(s: String): T
  fun toString(attributeValue: T): String?
}

interface MapAttributeValue<T : Any, DB : Any> {
  fun toAttributeValues(dbItem: DB): Map<String, T>
  fun toDb(attributeValues: Map<String, T>): DB

  interface Factory {
    fun <T : Any, DB : Any> create(type: KClass<DB>): MapAttributeValue<T, DB>
  }
}

/**
 * Converts values between the mutable `DynamoDBMapper` objects that defines the union of all key
 * and value attributes, and specific Tempest values.
 *
 * It is the caller's responsibility to check that the `DynamoDBMapper` instance can be safely
 * converted to the target type. If it cannot be, the behavior of this codec is undefined.
 */
interface Codec<A : Any, D : Any> {
  fun toDb(appItem: A): D
  fun toApp(dbItem: D): A
}

internal object IdentityCodec : Codec<Any, Any> {
  override fun toDb(appItem: Any): Any = appItem
  override fun toApp(dbItem: Any): Any = dbItem
}

/**
 * This binds a pair of objects: an app item and a db item. The property names of app item is a
 * subset of that of db item. The app item can have a primary constructor, vars, and vals, while
 * the db item can only have vars.
 */
internal class ReflectionCodec<A : Any, D : Any> private constructor(
  private val appItemConstructor: KFunction<A>?,
  private val appItemClassFactory: ClassFactory<A>,
  private val dbItemConstructor: KFunction<D>?,
  private val dbItemClassFactory: ClassFactory<D>,
  private val constructorParameters: List<ConstructorParameterBinding<A, D, Any?>>,
  private val varBindings: List<VarBinding<A, D, Any?>>,
  private val valBindings: List<ValBinding<A, D, Any?>>,
  private val prefixer: Prefixer<Any, D>
) : Codec<A, D> {

  override fun toDb(appItem: A): D {
    val dbItem = dbItemConstructor?.call() ?: dbItemClassFactory.newInstance()
    for (binding in constructorParameters) {
      binding.setDb(dbItem, binding.getApp(appItem))
    }
    for (binding in varBindings) {
      binding.setDb(dbItem, binding.getApp(appItem))
    }
    for (binding in valBindings) {
      binding.setDb(dbItem, binding.getApp(appItem))
    }
    return prefixer.addPrefix(dbItem)
  }

  override fun toApp(dbItem: D): A {
    val dbItem = prefixer.removePrefix(dbItem)
    val constructorArgs = constructorParameters
      .map { it.parameter to it.getDb(dbItem) }
      .toMap()
    val appItem =
      if (appItemConstructor != null && constructorArgs.size == appItemConstructor.parameters.size) {
        appItemConstructor.callBy(constructorArgs)
      } else {
        appItemClassFactory.newInstance()
      }
    for (binding in varBindings) {
      binding.setApp(appItem, binding.getDb(dbItem))
    }
    return appItem
  }

  /**
   * Adds prefixes to and removes prefixes from properties of a db item.
   */
  private class Prefixer<AV : Any, DB : Any>(
    private val attributePrefixes: List<AttributePrefix>,
    private val rawItemType: RawItemType,
    private val stringAttributeValue: StringAttributeValue<AV>,
    private val mapAttributeValue: MapAttributeValue<AV, DB>
  ) {

    fun addPrefix(dbItem: DB): DB {
      val attributeValues = mapAttributeValue.toAttributeValues(dbItem).toMutableMap()
      for ((attributeName, prefix, allowEmpty) in attributePrefixes) {
        val attributeValue = attributeValues[attributeName]
        if (attributeValue == null && allowEmpty) continue
        if (attributeValue == null) {
          attributeValues[attributeName] = attributeValue(prefix)
          continue
        }
        requireNotNull(attributeValue.s) {
          "Expect ${rawItemType.type}.$attributeName to be mapped to a string"
        }
        attributeValues[attributeName] = attributeValue(prefix + attributeValue.s)
      }
      return mapAttributeValue.toDb(attributeValues)
    }

    fun removePrefix(dbItem: DB): DB {
      val attributeValues = mapAttributeValue.toAttributeValues(dbItem).toMutableMap()
      for ((attributeName, prefix, nullable) in attributePrefixes) {
        if (nullable && attributeValues[attributeName]?.s == null) {
          // attributeValues[attributeName] = attributeValue("")
          continue
        }
        val attributeValue = requireNotNull(attributeValues[attributeName])
        requireNotNull(attributeValue.s) {
          "Expect ${rawItemType.type}.$attributeName to be mapped to a string"
        }
        attributeValues[attributeName] = attributeValue(attributeValue.s!!.removePrefix(prefix))
      }
      return mapAttributeValue.toDb(attributeValues)
    }

    private val AV.s: String?
      get() = stringAttributeValue.toString(this)

    private fun attributeValue(s: String): AV = stringAttributeValue.toAttributeValue(s)

    data class AttributePrefix(val attributeName: String, val prefix: String, val allowEmpty: Boolean = false)
  }

  internal class Factory(
    private val stringAttributeValue: StringAttributeValue<*>,
    private val mapAttributeValueFactory: MapAttributeValue.Factory
  ) {

    fun create(
      itemType: KClass<*>,
      itemAttributes: Map<String, ItemType.Attribute>,
      rawItemType: RawItemType
    ): Codec<Any, Any> {
      val dbItemConstructor = requireNotNull(rawItemType.type.defaultConstructor)
      require(dbItemConstructor.parameters.isEmpty()) { "Expect ${rawItemType.type} to have a zero argument constructor" }
      val appItemConstructorParameters = itemType.defaultConstructorParameters
      val dbItemProperties = rawItemType.type.memberProperties.associateBy { it.name }
      val constructorParameterBindings =
        mutableListOf<ConstructorParameterBinding<Any, Any, Any?>>()
      val varBindings = mutableListOf<VarBinding<Any, Any, Any?>>()
      val valBindings = mutableListOf<ValBinding<Any, Any, Any?>>()
      for (property in itemType.memberProperties) {
        val propertyName = property.name
        val itemAttribute = itemAttributes[propertyName] ?: continue
        val mappedProperties = itemAttribute.names
          .map { requireNotNull(dbItemProperties[it]) { "Expect ${rawItemType.type} to have property $propertyName" } }
        val mappedPropertyTypes = mappedProperties.map { it.returnType }.distinct()
        require(mappedPropertyTypes.size == 1) { "Expect mapped properties of $propertyName to have the same type: ${mappedProperties.map { it.name }}" }
        val expectedReturnType = requireNotNull(mappedPropertyTypes.single()).withNullability(false)
        val actualReturnType = property.returnType.withNullability(false)
        require(actualReturnType == expectedReturnType) { "Expect the return type of $itemType.${property.name} to be $expectedReturnType but was $actualReturnType" }
        if (appItemConstructorParameters.contains(propertyName)) {
          constructorParameterBindings.add(
            ConstructorParameterBinding(
              property as KProperty1<Any, Any?>,
              appItemConstructorParameters[propertyName]!!,
              mappedProperties
            )
          )
        } else if (property.isVar) {
          varBindings.add(
            VarBinding(property as KProperty1<Any, Any?>, mappedProperties)
          )
        } else {
          valBindings.add(ValBinding(property as KProperty1<Any, Any?>, mappedProperties))
        }
      }
      val attributePrefixes = itemAttributes.values
        .filter { attribute -> attribute.prefix.isNotEmpty() }
        .flatMap { attribute -> attribute.names.map { Prefixer.AttributePrefix(it, attribute.prefix, attribute.allowEmpty) } }
      return ReflectionCodec(
        itemType.defaultConstructor,
        ClassFactory.create(itemType.java),
        dbItemConstructor,
        ClassFactory.create(rawItemType.type.java),
        constructorParameterBindings,
        varBindings,
        valBindings,
        Prefixer(
          attributePrefixes,
          rawItemType,
          stringAttributeValue as StringAttributeValue<Any>,
          mapAttributeValueFactory.create(rawItemType.type)
        )
      )
    }
  }
}

/**
 * One app item property maps to one or more db item properties.
 */
private sealed class Binding<A, D, P> {
  abstract val appProperty: KProperty1<A, P>
  abstract val dbProperties: List<KProperty1<D, P>>

  fun getApp(value: A): P {
    if (!appProperty.isAccessible) {
      appProperty.javaField!!.trySetAccessible()
    }
    return appProperty.get(value)
  }

  fun getDb(value: D) = dbProperties[0].get(value)

  fun setDb(result: D, value: P) {
    for (rawProperty in dbProperties) {
      rawProperty.forceSet(result, value)
    }
  }
}

private class ConstructorParameterBinding<A, D, P>(
  override val appProperty: KProperty1<A, P>,
  val parameter: KParameter,
  override val dbProperties: List<KProperty1<D, P>>
) : Binding<A, D, P>()

private class ValBinding<A, D, P>(
  override val appProperty: KProperty1<A, P>,
  override val dbProperties: List<KProperty1<D, P>>
) : Binding<A, D, P>()

private class VarBinding<A, D, P>(
  override val appProperty: KProperty1<A, P>,
  override val dbProperties: List<KProperty1<D, P>>
) : Binding<A, D, P>() {

  fun setApp(result: A, value: P) {
    appProperty.forceSet(result, value)
  }
}

private val KProperty<*>.isVar: Boolean
  get() = javaField != null

private fun <T, R> KProperty1<T, R>.forceSet(receiver: T, value: R) {
  if (!isAccessible) {
    javaField!!.trySetAccessible()
  }
  if (this is KMutableProperty1<T, R>) {
    set(receiver, value)
    return
  }
  javaField!!.set(receiver, value)
}
