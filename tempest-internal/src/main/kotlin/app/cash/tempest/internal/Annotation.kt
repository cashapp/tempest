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

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty

interface ForIndexAnnotation<T : Annotation> {
  val type: KClass<T>
  fun name(annotation: T): String
}

internal fun <T : Annotation> ForIndexAnnotation<T>.secondaryIndexName(keyType: KClass<*>): String? {
  return keyType.findAnnotation(type)?.let(this::name)
}

interface AttributeAnnotation<T : Annotation> {
  val type: KClass<T>
  fun name(annotation: T): String
  fun names(annotation: T): Array<String>
  fun prefix(annotation: T): String
}

internal fun AttributeAnnotation<*>.hasAttributeAnnotation(
  property: KProperty<*>,
  constructorParameters: Map<String, KParameter>
): Boolean {
  return findAnnotation(property, constructorParameters) != null
}

internal fun <T : Annotation> AttributeAnnotation<T>.attributeMetadata(
  property: KProperty<*>,
  constructorParameters: Map<String, KParameter>
): AttributeMetadata {
  val annotation = findAnnotation(property, constructorParameters)
  return AttributeMetadata(
    annotation?.let(this::annotatedNames) ?: setOf(property.name),
    annotation?.let(this::prefix) ?: ""
  )
}

internal data class AttributeMetadata(
  val names: Set<String>,
  val prefix: String
)

private fun <T : Annotation> AttributeAnnotation<T>.findAnnotation(
  property: KProperty<*>,
  constructorParameters: Map<String, KParameter>
): T? {
  return property.findAnnotation(type)
    ?: constructorParameters[property.name]?.findAnnotation(type)
}

private fun <T : Annotation> AttributeAnnotation<T>.annotatedNames(annotation: T): Set<String>? {
  val names = names(annotation)
  val name = name(annotation)
  return when {
    names.isNotEmpty() -> {
      require(name.isEmpty()) { "Attribute annotation is ambiguous. name: $name, names: $names" }
      names.toSet()
    }
    name.isNotEmpty() -> setOf(name)
    else -> null
  }
}
