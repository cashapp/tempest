package app.cash.tempest2

/**
 * Resolves the table name for a given [LogicalTable] class.
 *
 * This allows table names to be overridden at runtime.
 */
interface TableNameResolver {
    fun resolveTableName(clazz: Class<*>, tableNameFromAnnotation: String?): String
}
