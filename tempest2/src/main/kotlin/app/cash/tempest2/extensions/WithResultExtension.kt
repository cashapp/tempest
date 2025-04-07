package app.cash.tempest2.extensions

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import software.amazon.awssdk.enhanced.dynamodb.*
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbExtensionContext.BeforeWrite
import software.amazon.awssdk.enhanced.dynamodb.extensions.ReadModification
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
    if (context.operationName() != OperationName.PUT_ITEM) {
      // Only works on putItem for now.
      return EMPTY_WRITE
    }

    // Make sure the current call set up the thread context for applying result.
    val trackerKey = currentRequestTrackerKey.get()
      ?: return EMPTY_WRITE

    // The tracker must exist if there is a key on the thread context.
    val trackedRequest = checkNotNull(itemTracker.get(trackerKey))

    // Add the current context item map to the result set.
    trackedRequest.items.add(context.items().toMap())
    if (trackedRequest.totalItems == trackedRequest.items.size) {
      // The requested items have all been returned, reset the current thread tracker so it is not leaked.
      currentRequestTrackerKey.set(null)
    }

    // No updates.
    return EMPTY_WRITE
  }

  override fun afterRead(context: DynamoDbExtensionContext.AfterRead?): ReadModification =
    EMPTY_READ

  companion object {
    @RequiresOptIn(message = "Requires WithResultExtension to be installed last on the DynamoEnhancedClient")
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class WithResultExtensionInstalledLast

    internal val currentRequestTrackerKey =
      object : ThreadLocal<TrackerKey?>() {
        override fun initialValue(): TrackerKey? = null
      }

    internal data class TrackerKey(
      val requestId: UUID
    ) {
      companion object {
        fun fromRequestId(uuid: UUID) =
          TrackerKey(
            requestId = uuid
          )

        fun generate() =
          TrackerKey(
            requestId = UUID.randomUUID()
          )
      }
    }

    internal data class Tracker(
      /**
       * The total expected items.
       */
      val totalItems: Int,
    ) {
      val items: MutableSet<Map<String, AttributeValue>> = mutableSetOf()
    }

    /**
     * Initiate the thread for the current request.  This must be called from the same thread as the request is sent.
     * It relies on the fact that dynamo processes the request item in the same thread before going async.
     *
     * @param totalItems The total items that are updated.  For PutItem this is always 1 (default).
     *  Support for batch/transact will update this value appropriately.
     *
     * @return a key to be used for getResult(s)
     */
    internal fun initiateRequest(totalItems: Int = 1) : UUID {
      val trackerKey = TrackerKey.generate()
      val tracker = Tracker(totalItems)

      itemTracker.put(trackerKey, tracker)
      currentRequestTrackerKey.set(trackerKey)

      return trackerKey.requestId
    }

    /**
     * Gets the updated items after the request has completed.
     *
     * @param requestId the requestId returned by initiateRequest.
     */
    internal fun getResults(requestId: UUID) : Set<Map<String, AttributeValue>> {
      val trackerKey = TrackerKey.fromRequestId(requestId)
      val tracker = itemTracker.remove(trackerKey)!!

      val result = tracker.items.toSet()
      check(result.size == tracker.totalItems) {
        "Resulting Item was not updated. Did you forget to install the ApplyUpdatesExtension?"
      }
      return result
    }

    /**
     * Gets the updated item when only one item is updated after the request has completed.
     *
     * @param requestId the requestId returned by initiateRequest.
     */
    internal fun getResult(requestId: UUID) : Map<String, AttributeValue> =
      getResults(requestId).single()

    /**
     * When an error occurs on the dynamo client, call onError before returning/throwing the exception
     * to the caller to reset thread local state and release the tracker.
     *
     * @param requestId the requestId returned by initiateRequest.
     */
    internal fun onError(requestId: UUID) {
      itemTracker.remove(TrackerKey.fromRequestId(requestId))
      currentRequestTrackerKey.set(null)
    }

    private val EMPTY_WRITE = WriteModification.builder().build()
    private val EMPTY_READ = ReadModification.builder().build()
    private val itemTracker : MutableMap<TrackerKey, Tracker> = ConcurrentHashMap<TrackerKey, Tracker>()

    /**
     * Create an instance of the extension.
     */
    fun create() : WithResultExtension {
      return WithResultExtension()
    }
  }
}