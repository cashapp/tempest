package app.cash.tempest

/**
 * Maps an item class property to one or more attributes in a DynamoDB table.
 *
 * If this mapped to a primary range key, it must have a prefix.
 */
annotation class Attribute(
  val name: String = "",
  val names: Array<String> = [],
  val prefix: String = ""
)

/**
 * Maps an key class to a global or local secondary index in a DynamoDB table.
 */
annotation class ForIndex(
  val name: String = ""
)
