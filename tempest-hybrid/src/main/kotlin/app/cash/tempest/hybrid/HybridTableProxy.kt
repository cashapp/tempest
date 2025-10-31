package app.cash.tempest.hybrid

import app.cash.tempest.InlineView
import app.cash.tempest.LogicalTable
import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class HybridTableProxy(
  private val regularTable: LogicalTable<*>,
  private val hybridAnnotation: HybridTable,
  private val itemClass: Class<*>,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig
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
      HybridViewProxy(regularView, hybridAnnotation, itemClass, s3Client, objectMapper, hybridConfig)
    ) as InlineView<*, *>
  }
}
