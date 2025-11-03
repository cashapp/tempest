package app.cash.tempest2.internal

import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchemaParams
import software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchemaParams
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable
import java.lang.invoke.MethodHandles
import java.util.concurrent.ConcurrentHashMap

object TableSchemaFactory {

  private val schemas = ConcurrentHashMap<Class<*>, TableSchema<*>>()

  /**
   * Compute the TableSchema, which a moderately expensive operation, and cache the result.
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> create(clazz: Class<*>): TableSchema<T> {
    return schemas.getOrPut(clazz) {
      // This is ported from TableSchema.fromClass which was the previous implementation.
      // The modification is to use BeanTableSchemaParams and ImmutableTableSchemaParams:
      //   Generally, this method should be preferred over from*(Class) because it allows
      //   you to use a custom MethodHandles.Lookup instance, which is necessary when your
      //   application runs in an environment where your application code and dependencies
      //   like the AWS SDK for Java are loaded by different classloaders.
      var methodHandle : MethodHandles.Lookup = MethodHandles.lookup();
      try {
        if (clazz.classLoader != TableSchemaFactory.javaClass.classLoader) {
          // Try and use a handle that is scoped to the target class if the class is from a different ClassLoader
          methodHandle = MethodHandles.privateLookupIn(
            clazz,
            MethodHandles.lookup()
          )
        }
      } catch (_: Exception) {
        // In some circumstances the above may fail, so fall back to the default behaviour
        methodHandle = MethodHandles.lookup()
      }
      if (clazz.getAnnotation<DynamoDbImmutable>(DynamoDbImmutable::class.java) != null) {
        TableSchema.fromImmutableClass(ImmutableTableSchemaParams.builder(clazz).lookup(methodHandle).build())
      } else if (clazz.getAnnotation<DynamoDbBean>(DynamoDbBean::class.java) != null) {
        TableSchema.fromBean(BeanTableSchemaParams.builder(clazz).lookup(methodHandle).build())
      } else {
        throw IllegalArgumentException("Class does not appear to be a valid DynamoDb annotated class. [class = \"$clazz\"]")
      }
    } as TableSchema<T>
  }

  inline fun <reified T> create(): TableSchema<T> {
    return create(T::class.java)
  }
}
