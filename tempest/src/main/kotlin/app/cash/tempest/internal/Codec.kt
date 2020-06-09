package app.cash.tempest.internal

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

internal interface Codec<A : Any, D : Any> {
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
  private val appItemConstructor: KFunction<A>,
  private val dbItemConstructor: KFunction<D>,
  private val constructorParameters: List<ConstructorParameterBinding<A, D, Any?>>,
  private val varBindings: List<VarBinding<A, D, Any?>>,
  private val valBindings: List<ValBinding<A, D, Any?>>,
  private val prefixer: Prefixer<D>
) : Codec<A, D> {

  override fun toDb(appItem: A): D {
    val dbItem = dbItemConstructor.call()
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
    val item = appItemConstructor.callBy(constructorArgs)
    for (binding in varBindings) {
      binding.setApp(item, binding.getDb(dbItem))
    }
    return item
  }

  internal class Factory {

    fun create(
      itemType: KClass<*>,
      itemAttributes: Map<String, ItemType.Attribute>,
      rawItemType: RawItemType
    ): Codec<Any, Any> {
      val appItemConstructor = requireNotNull(itemType.primaryConstructor)
      val dbItemConstructor = requireNotNull(rawItemType.type.primaryConstructor)
      val itemConstructorParameters = appItemConstructor
          .parameters.associateBy { requireNotNull(it.name) }
      val rawItemProperties = rawItemType.type.memberProperties.associateBy { it.name }
      val constructorParameters = mutableListOf<ConstructorParameterBinding<Any, Any, Any?>>()
      val mutableProperties = mutableListOf<VarBinding<Any, Any, Any?>>()
      val otherProperties = mutableListOf<ValBinding<Any, Any, Any?>>()
      for (property in itemType.memberProperties) {
        val propertyName = property.name
        val mappedProperties = requireNotNull(itemAttributes[propertyName]).names
            .map { requireNotNull(rawItemProperties[it]) }
        if (itemConstructorParameters.contains(propertyName)) {
          constructorParameters.add(
              ConstructorParameterBinding(
                  property as KProperty1<Any, Any?>,
                  itemConstructorParameters[propertyName]!!,
                  mappedProperties))
        } else if (property is KMutableProperty<*>) {
          mutableProperties.add(
              VarBinding(
                  property as KMutableProperty1<Any, Any?>,
                  mappedProperties))
        } else {
          otherProperties.add(ValBinding(property as KProperty1<Any, Any?>, mappedProperties))
        }
      }
      val prefixer = Prefixer<Any>(itemAttributes, rawItemType)
      return ReflectionCodec(
          appItemConstructor,
          dbItemConstructor,
          constructorParameters,
          mutableProperties,
          otherProperties,
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
  override val appProperty: KMutableProperty1<A, P>,
  override val dbProperties: List<KProperty1<D, P>>
) : Binding<A, D, P>() {

  fun setApp(result: A, value: P) {
    appProperty.set(result, value)
  }
}
