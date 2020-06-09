package misk.logicaldb.internal

import misk.logicaldb.LogicalDb
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

internal class ProxyFactory {

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
        invocationHandler(methodHandlers, instance)) as T
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
          method.declaringClass == LogicalDb::class.java || method.declaringClass == Any::class.java -> {
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

internal interface MethodHandler {
  fun invoke(args: Array<out Any>): Any?
}

internal class GetterMethodHandler(private val value: Any?) : MethodHandler {
  override fun invoke(args: Array<out Any>): Any? = value
}
