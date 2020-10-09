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

import app.cash.tempest.Codec
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

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
  private val prefixer: Prefixer<D>
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
      appItemConstructor?.callBy(constructorArgs) ?: appItemClassFactory.newInstance()
    for (binding in varBindings) {
      binding.setApp(appItem, binding.getDb(dbItem))
    }
    return appItem
  }

  internal class Factory {

    fun create(
      itemType: KClass<*>,
      itemAttributes: Map<String, ItemType.Attribute>,
      rawItemType: RawItemType
    ): Codec<Any, Any> {
      val dbItemConstructor = requireNotNull(rawItemType.type.primaryConstructor ?: rawItemType.type.constructors.singleOrNull())
      require(dbItemConstructor.parameters.isEmpty()) { "Expect ${rawItemType.type} to have a zero argument constructor" }
      val appItemConstructorParameters = itemType.primaryConstructorParameters
      val rawItemProperties = rawItemType.type.memberProperties.associateBy { it.name }
      val constructorParameterBindings = mutableListOf<ConstructorParameterBinding<Any, Any, Any?>>()
      val varBindings = mutableListOf<VarBinding<Any, Any, Any?>>()
      val valBindings = mutableListOf<ValBinding<Any, Any, Any?>>()
      for (property in itemType.memberProperties) {
        val propertyName = property.name
        val itemAttribute = itemAttributes[propertyName] ?: continue
        val mappedProperties = itemAttribute.names
            .map { requireNotNull(rawItemProperties[it]) { "Expect ${rawItemType.type} to have property $propertyName" } }
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
                  mappedProperties))
        } else if (property.isVar) {
          varBindings.add(
              VarBinding(property as KProperty1<Any, Any?>, mappedProperties))
        } else {
          valBindings.add(ValBinding(property as KProperty1<Any, Any?>, mappedProperties))
        }
      }
      val prefixer = Prefixer<Any>(itemAttributes, rawItemType)
      return ReflectionCodec(
          itemType.primaryConstructor,
          ClassFactory.create(itemType.java),
          dbItemConstructor,
          ClassFactory.create(rawItemType.type.java),
          constructorParameterBindings,
          varBindings,
          valBindings,
          prefixer
      )
    }
  }
}

/**
 * Adds prefixes to and removes prefixes from properties of a db item.
 */
private class Prefixer<D : Any>(
  private val itemAttributes: Map<String, ItemType.Attribute>,
  private val rawItemType: RawItemType
) {

  fun addPrefix(dbItem: D): D {
    val attributeValues = rawItemType.tableModel.convert(dbItem).toMutableMap()
    val attributePrefixes = itemAttributes.values
        .filter { attribute -> attribute.prefix.isNotEmpty() }
        .flatMap { attribute -> attribute.names.map { it to attribute.prefix } }
    for ((attributeName, prefix) in attributePrefixes) {
      val attributeValue = attributeValues[attributeName]
      if (attributeValue == null) {
        attributeValues[attributeName] = AttributeValue(prefix)
        continue
      }
      requireNotNull(attributeValue.s) {
        "Expect ${rawItemType.type}.$attributeName to be mapped to a string"
      }
      attributeValues[attributeName] = AttributeValue(prefix + attributeValue.s)
    }
    return rawItemType.tableModel.unconvert(attributeValues) as D
  }

  fun removePrefix(dbItem: D): D {
    val attributeValues = rawItemType.tableModel.convert(dbItem).toMutableMap()
    val attributePrefixes = itemAttributes.values
        .filter { attribute -> attribute.prefix.isNotEmpty() }
        .flatMap { attribute -> attribute.names.map { it to attribute.prefix } }
    for ((attributeName, prefix) in attributePrefixes) {
      val attributeValue = requireNotNull(attributeValues[attributeName])
      requireNotNull(attributeValue.s) {
        "Expect ${rawItemType.type}.$attributeName to be mapped to a string"
      }
      attributeValues[attributeName] = AttributeValue(attributeValue.s.removePrefix(prefix))
    }
    return rawItemType.tableModel.unconvert(attributeValues) as D
  }
}

/**
 * One app item property maps to one or more db item properties.
 */
private sealed class Binding<A, D, P> {
  abstract val appProperty: KProperty1<A, P>
  abstract val dbProperties: List<KProperty1<D, P>>

  fun getApp(value: A) = appProperty.get(value)

  fun getDb(value: D) = dbProperties[0].get(value)

  fun setDb(result: D, value: P) {
    for (rawProperty in dbProperties) {
      if (!rawProperty.isAccessible) {
        rawProperty.javaField?.trySetAccessible()
      }
      (rawProperty as KMutableProperty1<D, P>).set(result, value)
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

val KProperty<*>.isVar: Boolean
  get() = javaField != null

fun <T, R> KProperty1<T, R>.forceSet(receiver: T, value: R) {
  if (!isAccessible) {
    javaField!!.trySetAccessible()
  }
  if (this is KMutableProperty1<T, R>) {
    set(receiver, value)
    return
  }
  javaField!!.set(receiver, value)
}
