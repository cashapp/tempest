package app.cash.tempest2.serializer

import com.squareup.moshi.Moshi
import com.squareup.wire.WireJsonAdapterFactory
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A dynamo serializer/deserializer for protos wrapped in a Set.
 * Converts a [Set] of type [T] to its JSON serialization.
 *
 * Example Usage:
 * ```
 *    class MyProtoSetConverter : DyProtoSetSerializer<MyProto>(MyProto::class.java)
 *
 *    @DynamoDbBean
 *    data class DyItem(
 *      @get:DynamoDbConvertedBy(MyProtoSetConverter::class)
 *      var set_of_values: Set<MyProto>? = null,
 *    )
 * ```
 */
internal open class DySetSerializer<T>(
  private val clazz: Class<T>
) : AttributeConverter<Set<T>> {
  private val moshi = Moshi.Builder()
    .add(WireJsonAdapterFactory())
    .build()

  private val adapter = moshi.adapter(clazz)

  override fun transformFrom(input: Set<T>?): AttributeValue? =
    input?.let {
      AttributeValue.builder()
        .l(it.map { v -> AttributeValue.builder().s(adapter.toJson(v)).build() })
        .build()
    }

  override fun transformTo(input: AttributeValue?): Set<T> =
    input?.l()?.map { adapter.fromJson(it.s())!! }?.toSet() ?: emptySet()

  override fun type(): EnhancedType<Set<T>> = EnhancedType.setOf(clazz)

  override fun attributeValueType() = AttributeValueType.S
}
