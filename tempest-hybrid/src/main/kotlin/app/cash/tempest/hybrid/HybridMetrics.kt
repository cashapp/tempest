package app.cash.tempest.hybrid

/**
 * Optional interface for collecting metrics from hybrid storage operations.
 *
 * This interface is designed to be minimal and easy to remove. When null,
 * no metrics are collected with zero overhead.
 */
interface HybridMetrics {
  /**
   * Records a metric event. Called for S3 hydrations and batch operations.
   */
  fun record(event: MetricEvent)
}

/**
 * Sealed class representing all metric events.
 * Using a sealed class makes it easy to extend or remove metrics entirely.
 */
sealed class MetricEvent {
  /**
   * Records a single S3 hydration attempt.
   *
   * @param operation The DynamoDB operation type (query, scan, getItem, etc.)
   * @param success Whether the hydration succeeded
   * @param latencyMs Time taken for the S3 operation in milliseconds
   */
  data class Hydration(
    val operation: String,
    val success: Boolean,
    val latencyMs: Long
  ) : MetricEvent()

  /**
   * Records completion of a batch operation with multiple items.
   *
   * @param operation The DynamoDB operation type
   * @param itemCount Total number of items that needed hydration
   * @param successCount Number of items successfully hydrated
   */
  data class BatchComplete(
    val operation: String,
    val itemCount: Int,
    val successCount: Int
  ) : MetricEvent()
}

/**
 * No-op implementation that does nothing. Useful for testing or when you want
 * the interface without any overhead.
 */
object NoOpMetrics : HybridMetrics {
  override fun record(event: MetricEvent) {
    // Intentionally empty - no-op implementation
  }
}

/**
 * Simple implementation that counts events. Useful for testing.
 */
class CountingMetrics : HybridMetrics {
  val events = mutableListOf<MetricEvent>()

  @Synchronized
  override fun record(event: MetricEvent) {
    events.add(event)
  }

  fun reset() {
    events.clear()
  }

  fun hydrationCount(): Int = events.filterIsInstance<MetricEvent.Hydration>().size
  fun successCount(): Int = events.filterIsInstance<MetricEvent.Hydration>().count { it.success }
  fun failureCount(): Int = events.filterIsInstance<MetricEvent.Hydration>().count { !it.success }
}

/**
 * Conditional wrapper that only records metrics when a condition is met.
 * Useful for runtime feature flags or sampling.
 */
class ConditionalMetrics(
  private val delegate: HybridMetrics,
  private val condition: () -> Boolean
) : HybridMetrics {
  override fun record(event: MetricEvent) {
    if (condition()) {
      delegate.record(event)
    }
  }
}