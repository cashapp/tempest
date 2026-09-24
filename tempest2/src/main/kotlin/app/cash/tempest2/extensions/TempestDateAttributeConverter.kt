package app.cash.tempest2.extensions

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.enhanced.dynamodb.internal.converter.attribute.InstantAsStringAttributeConverter
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.Date

@Suppress("UNCHECKED_CAST")
class TempestDateAttributeConverterProvider : AttributeConverterProvider {
  override fun <T : Any> converterFor(enhancedType: EnhancedType<T>): AttributeConverter<T>? =
    when (enhancedType.rawClass()) {
      Date::class.java -> DATE_ATTRIBUTE_CONVERTER as AttributeConverter<T>
      else -> null
    }

  companion object {
    private val DATE_ATTRIBUTE_CONVERTER = TempestDateAttributeConverter()
  }
}

class TempestDateAttributeConverter : AttributeConverter<Date> {

  override fun transformFrom(input: Date): AttributeValue = INSTANT_CONVERTER.transformFrom(input.toInstant())

  override fun transformTo(input: AttributeValue): Date = Date.from(INSTANT_CONVERTER.transformTo(input))

  override fun type(): EnhancedType<Date> = EnhancedType.of(Date::class.java)

  override fun attributeValueType(): AttributeValueType = AttributeValueType.S

  companion object {
    /**
     * Use instant converter for ISO 8601 compatibility when converting Date
     */
    private val INSTANT_CONVERTER = InstantAsStringAttributeConverter.create()
  }
}