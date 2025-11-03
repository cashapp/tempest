/*
 * Copyright 2024 Square Inc.
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
package app.cash.tempest.hybrid

import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ExecutorService

internal class HybridTableProxy(
  private val regularTable: LogicalTable<*>,
  private val hybridAnnotation: HybridTable,
  private val itemClass: Class<*>,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig,
  private val executorService: ExecutorService
) : InvocationHandler {
  
  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
    
    // If it's a view getter method, wrap it with hybrid functionality
    if (method.returnType.isAssignableFrom(InlineView::class.java)) {
      
      val regularView = method.invoke(regularTable, *(args ?: emptyArray())) as InlineView<*, *>
      
      // Wrap with hybrid view
      return createHybridView(regularView)
    }
    
    // Default: delegate to regular table
    return method.invoke(regularTable, *(args ?: emptyArray()))
  }
  
  private fun createHybridView(regularView: InlineView<*, *>): InlineView<*, *> {

    return Proxy.newProxyInstance(
      regularView.javaClass.classLoader,
      arrayOf(HybridInlineView::class.java, InlineView::class.java),
      HybridViewProxy(
        regularView,
        hybridAnnotation,
        itemClass,
        s3Client,
        objectMapper,
        hybridConfig,
        executorService
      )
    ) as InlineView<*, *>
  }
}
