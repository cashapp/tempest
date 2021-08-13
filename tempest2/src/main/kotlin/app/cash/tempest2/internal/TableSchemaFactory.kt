package app.cash.tempest2.internal

import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import java.util.concurrent.ConcurrentHashMap

object TableSchemaFactory {

  private val schemas = ConcurrentHashMap<Class<*>, TableSchema<*>>()

  /**
   * Compute the TableSchema, which a moderately expensive operation, and cache the result.
   */
  fun <T> create(clazz: Class<*>): TableSchema<T> {
    return schemas.getOrPut(clazz) { TableSchema.fromClass(clazz) } as TableSchema<T>
  }

  inline fun <reified T> create(): TableSchema<T> {
    return create(T::class.java)
  }
}
