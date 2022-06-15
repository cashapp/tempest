package app.cash.tempest2.serializer

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.wire.WireJsonAdapterFactory
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A dynamo serializer/deserializer for protos.
 * Converts a proto of type [T] to its JSON serialization.
 *
 * Example Usage:
 * ```
 *    class MyProtoConverter : DyProtoSetSerializer<MyProto>(MyProto::class.java)
 *
 *    @DynamoDbBean
 *    data class DyItem(
 *      @get:DynamoDbConvertedBy(MyProtoConverter::class)
 *      var my_proto_value: MyProto? = null,
 *    )
 * ```
 */
open class DyProtoSerializer<T>(
  private val clazz: Class<T>
) : AttributeConverter<T> {
  private val moshi = Moshi.Builder()
    .add(WireJsonAdapterFactory())
    .add(KotlinJsonAdapterFactory())
    .build()

  private val adapter = moshi.adapter(clazz)

  override fun transformFrom(input: T?): AttributeValue =
    AttributeValue.builder()
      .s(input?.let { adapter.toJson(it) })
      .build()

  override fun transformTo(input: AttributeValue?): T? =
    input?.let { adapter.fromJson(it.s()) }

  override fun type(): EnhancedType<T> = EnhancedType.of(clazz)

  override fun attributeValueType() = AttributeValueType.S
}
