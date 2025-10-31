package app.cash.tempest.hybrid

import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.kotlinFunction

internal class HybridDbProxy(
  private val hybridDb: HybridLogicalDbImpl,
  private val regularDb: LogicalDb,
  private val s3Client: AmazonS3,
  private val objectMapper: ObjectMapper,
  private val hybridConfig: HybridConfig
) : InvocationHandler {
  
  override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
    
    // If it's a method from HybridLogicalDb, delegate to hybridDb
    if (method.declaringClass == HybridLogicalDb::class.java) {
      return method.invoke(hybridDb, *(args ?: emptyArray()))
    }
    
    // If it's a table getter method, wrap it with hybrid functionality
    val kotlinFunction = method.kotlinFunction
    if (kotlinFunction != null && method.returnType.isAssignableFrom(LogicalTable::class.java)) {
      
      val regularTable = method.invoke(regularDb, *(args ?: emptyArray())) as LogicalTable<*>
      
      // Check if the table's item class has @HybridTable annotation
      val itemClass = getTableItemClass(regularTable)
      val hybridAnnotation = itemClass?.kotlin?.findAnnotation<HybridTable>()
      
      if (hybridAnnotation != null) {
        // Wrap with hybrid functionality
        return createHybridTable(regularTable, hybridAnnotation, itemClass)
      }
    }
    
    // Default: delegate to regular DB
    return method.invoke(regularDb, *(args ?: emptyArray()))
  }
  
  private fun getTableItemClass(table: LogicalTable<*>): Class<*>? {
    // Extract the item class from the LogicalTable generic type
    val genericType = table.javaClass.genericInterfaces
      .filterIsInstance<java.lang.reflect.ParameterizedType>()
      .firstOrNull { it.rawType == LogicalTable::class.java }
    
    return genericType?.actualTypeArguments?.firstOrNull() as? Class<*>
  }
  
  private fun createHybridTable(
    regularTable: LogicalTable<*>,
    hybridAnnotation: HybridTable,
    itemClass: Class<*>
  ): LogicalTable<*> {
    
    return Proxy.newProxyInstance(
      regularTable.javaClass.classLoader,
      regularTable.javaClass.interfaces,
      HybridTableProxy(regularTable, hybridAnnotation, itemClass, s3Client, objectMapper, hybridConfig)
    ) as LogicalTable<*>
  }
}
