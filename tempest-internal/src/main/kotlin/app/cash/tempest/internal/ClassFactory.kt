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

import java.lang.reflect.InvocationTargetException

/**
 * Magic that creates instances of arbitrary concrete classes. Derived from Gson's UnsafeAllocator
 * and ConstructorConstructor classes.
 *
 * @author Joel Leitch
 * @author Jesse Wilson
 */
abstract class ClassFactory<T> {
  @Throws(
    InvocationTargetException::class,
    IllegalAccessException::class,
    InstantiationException::class
  ) abstract fun newInstance(): T

  companion object {
    fun <T> create(rawType: Class<*>): ClassFactory<T> {
      // Try to find a no-args constructor. May be any visibility including private.
      try {
        val constructor = rawType.getDeclaredConstructor()
        constructor.isAccessible = true
        return object : ClassFactory<T>() {
          @Throws(
            IllegalAccessException::class,
            InvocationTargetException::class,
            InstantiationException::class
          ) override fun newInstance(): T {
            return constructor.newInstance(null) as T
          }

          override fun toString(): String {
            return rawType.name
          }
        }
      } catch (ignored: NoSuchMethodException) {
        // No no-args constructor. Fall back to something more magical...
      }

      // Try the JVM's Unsafe mechanism.
      // public class Unsafe {
      //   public Object allocateInstance(Class<?> type);
      // }
      try {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val f = unsafeClass.getDeclaredField("theUnsafe")
        f.isAccessible = true
        val unsafe = f[null]
        val allocateInstance = unsafeClass.getMethod(
          "allocateInstance",
          Class::class.java
        )
        return object : ClassFactory<T>() {
          @Throws(
            InvocationTargetException::class,
            IllegalAccessException::class
          ) override fun newInstance(): T {
            return allocateInstance.invoke(unsafe, rawType) as T
          }

          override fun toString(): String {
            return rawType.name
          }
        }
      } catch (e: IllegalAccessException) {
        throw AssertionError()
      } catch (ignored: ClassNotFoundException) {
        // Not the expected version of the Oracle Java library!
      } catch (ignored: NoSuchMethodException) {
      } catch (ignored: NoSuchFieldException) {
      }
      throw IllegalArgumentException("cannot construct instances of " + rawType.name)
    }
  }
}
