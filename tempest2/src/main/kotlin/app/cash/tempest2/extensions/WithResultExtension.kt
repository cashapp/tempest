package app.cash.tempest2.extensions

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbExtensionContext
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbExtensionContext.BeforeWrite
import software.amazon.awssdk.enhanced.dynamodb.extensions.WriteModification
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.OperationName
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

/**
 * Enables WithResult APIs that reflect the auto generated updates to the item in the response.
 *
 * This extension must be installed to use these APIs.
 * This extension must be installed last!
 */
class WithResultExtension private constructor() : DynamoDbEnhancedClientExtension {

  /**
   * @param context The [DynamoDbExtensionContext.BeforeWrite] context containing the state of the execution.
   * @return WriteModification Instance updated with attribute updated with Extension.
   */
  override fun beforeWrite(context: BeforeWrite): WriteModification {
    when (context.operationName()) {
      OperationName.PUT_ITEM -> inMemoryItemsResult.get()?.add(context.items().toMap())
      else -> {}
    }

    return EMPTY_WRITE
  }

  companion object {
    @RequiresOptIn(message = "Requires WithResultExtension to be installed last on the DynamoEnhancedClient")
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class WithResultExtensionInstalledLast

    internal val inMemoryItemsResult =
      object : ThreadLocal<MutableSet<Map<String, AttributeValue>>?>() {
        override fun initialValue(): MutableSet<Map<String, AttributeValue>>? = null
      }

    /**
     * Run the operation, tracking updates to the item(s) and receive the result and updated item attribute maps.
     *
     * @param operation a method that executes the dynamo call.
     * @param expectedItemsCount the expected items to be tracked
     * @param resultOp a method that receives the result of [operation] and the updated item attribute maps.
     *
     * @return the return value of [resultOp]
     */
    internal fun <T, R> runWithResult(
      operation: () -> T,
      expectedItemsCount: Int = 1,
      resultOp: (T, Set<Map<String, AttributeValue>>) -> R
    ): R {
      // Set up thread local.
      val resultSet = mutableSetOf<Map<String, AttributeValue>>().apply {
        inMemoryItemsResult.set(this)
      }

      // Call the dynamo operation.
      val operationResult: T
      try {
        operationResult = operation()
      } finally {
        // Clean up thread local.
        inMemoryItemsResult.set(null)
      }

      check(resultSet.size == expectedItemsCount) {
        "Resulting Item was not updated. Did you forget to install the ApplyUpdatesExtension?"
      }

      // Process the result and the updated items.
      return resultOp(operationResult, resultSet.toSet())
    }

    private val EMPTY_WRITE = WriteModification.builder().build()

    /**
     * Create an instance of the extension.
     */
    fun create(): WithResultExtension {
      return WithResultExtension()
    }
  }
}