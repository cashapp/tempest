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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

object ProxyFactory {

  fun <T : Any> create(
    type: KClass<T>,
    methodHandlers: Map<Method, MethodHandler>,
    instance: Any
  ): T {
    val classLoader = type.java.classLoader
    @Suppress("UNCHECKED_CAST") // The proxy implements the requested interface.
    return Proxy.newProxyInstance(
      classLoader,
      arrayOf<Class<*>>(type.java),
      invocationHandler(methodHandlers, instance)
    ) as T
  }

  private fun invocationHandler(
    methodHandlers: Map<Method, MethodHandler>,
    instance: Any
  ): InvocationHandler {
    return object : InvocationHandler {
      override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val handler = methodHandlers[method]
        return when {
          handler != null -> {
            val result = handler.invoke(args ?: arrayOf())
            if (result == this) proxy else result
          }
          method.declaringClass.isAssignableFrom(instance.javaClass) || method.declaringClass == Any::class.java -> {
            try {
              val result = method.invoke(instance, *(args ?: arrayOf()))
              if (result == this) proxy else result
            } catch (e: InvocationTargetException) {
              throw e.cause!!
            }
          }
          else -> throw UnsupportedOperationException("unexpected call to $method")
        }
      }
    }
  }
}

interface MethodHandler {
  fun invoke(args: Array<out Any>): Any?
}

class GetterMethodHandler(private val value: Any?) : MethodHandler {
  override fun invoke(args: Array<out Any>): Any? = value
}
