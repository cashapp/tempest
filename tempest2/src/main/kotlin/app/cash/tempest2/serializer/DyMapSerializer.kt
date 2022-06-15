package app.cash.tempest2.serializer

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.wire.WireJsonAdapterFactory
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * A dynamo serializer/deserializer for a MutableMap.
 * Converts a [MutableMap] of type [K, V] to its JSON serialization.
 *
 * Example Usage:
 * ```
 *    class MyMapConverter : DyMapSerializer<KeyClass, ValueClass>(KeyClass:class.java, ValueClass::class.java)
 *
 *    @DynamoDbBean
 *    data class DyItem(
 *      @get:DynamoDbConvertedBy(MyMapConverter::class)
 *      var myMap: MutableMap<KeyClass, ValueClass>? = null,
 *    )
 * ```
 */
internal open class DyMapSerializer<K, V>(
  private val keyClazz: Class<K>,
  private val valueClazz: Class<V>,
) : AttributeConverter<MutableMap<K, V>?> {
  private val moshi = Moshi.Builder()
    .add(WireJsonAdapterFactory())
    .add(KotlinJsonAdapterFactory())
    .build()

  private val keyAdapter = moshi.adapter(keyClazz)
  private val valueAdapter = moshi.adapter(valueClazz)

  override fun transformFrom(input: MutableMap<K, V>?): AttributeValue? =
    input?.let {
      AttributeValue.builder()
        .m(
          it.mapKeys { e -> keyAdapter.toJson(e.key) }
            .mapValues { e -> AttributeValue.builder().s(valueAdapter.toJson(e.value)).build() }
        )
        .build()
    }

  override fun transformTo(input: AttributeValue?): MutableMap<K, V>? =
    input?.m()?.mapKeys { keyAdapter.fromJson(it.key)!! }
      ?.mapValues { valueAdapter.fromJson(it.value.s())!! }?.toMutableMap()

  override fun type(): EnhancedType<MutableMap<K, V>?> =
    EnhancedType.mapOf(keyClazz, valueClazz)

  override fun attributeValueType() = AttributeValueType.S
}
