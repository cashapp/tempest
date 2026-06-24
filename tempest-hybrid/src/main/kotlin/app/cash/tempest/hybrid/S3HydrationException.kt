package app.cash.tempest.hybrid

/**
 * Exception thrown when S3 hydration fails and the error strategy is FAIL_FAST.
 *
 * @param s3Pointer The S3 pointer that failed to hydrate
 * @param cause The underlying exception that caused the failure
 */
class S3HydrationException(
  val s3Pointer: String,
  override val message: String,
  override val cause: Throwable? = null
) : RuntimeException(message, cause) {

  constructor(s3Pointer: String, cause: Throwable) : this(
    s3Pointer = s3Pointer,
    message = "Failed to hydrate S3 pointer: $s3Pointer - ${cause.message}",
    cause = cause
  )
}