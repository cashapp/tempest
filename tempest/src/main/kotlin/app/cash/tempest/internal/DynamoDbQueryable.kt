/*
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.tempest.internal

import app.cash.tempest.BeginsWith
import app.cash.tempest.Between
import app.cash.tempest.FilterExpression
import app.cash.tempest.KeyCondition
import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.Queryable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BETWEEN
import com.amazonaws.services.dynamodbv2.model.Condition
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity
import com.amazonaws.services.dynamodbv2.model.Select.SPECIFIC_ATTRIBUTES
import kotlin.reflect.KClass

internal class DynamoDbQueryable<K : Any, I : Any>(
  private val hashKeyName: String,
  private val rangeKeyName: String,
  private val secondaryIndexName: String?,
  private val specificAttributeNames: Set<String>,
  private val keyCodec: Codec<K, Any>,
  private val itemCodec: Codec<I, Any>,
  private val rawType: KClass<Any>,
  private val tableModel: DynamoDBMapperTableModel<Any>,
  private val dynamoDbMapper: DynamoDBMapper
) : Queryable<K, I> {

  override fun query(
    keyCondition: KeyCondition<K>,
    asc: Boolean,
    pageSize: Int,
    consistentRead: Boolean,
    returnConsumedCapacity: ReturnConsumedCapacity,
    filterExpression: FilterExpression?,
    initialOffset: Offset<K>?
  ): Page<K, I> {
    val query = DynamoDBQueryExpression<Any>()
    query.apply(keyCondition)
    query.isScanIndexForward = asc
    query.isConsistentRead = consistentRead
    query.limit = pageSize
    if (secondaryIndexName != null) {
      query.withIndexName(secondaryIndexName)
    }
    if (specificAttributeNames.isNotEmpty()) {
      query.withSelect(SPECIFIC_ATTRIBUTES)
      val specificAttributeNamesAliased = mutableListOf<String>()
      val expressionAttributeNames = mutableMapOf<String, String>()
      for (name in specificAttributeNames) {
        val upperName = name.toUpperCase()
        if (reservedWords.contains(upperName)) {
          val alias = "#$name"
          specificAttributeNamesAliased.add(alias)
          expressionAttributeNames[alias] = name
        } else {
          specificAttributeNamesAliased.add(name)
        }
      }
      query.projectionExpression = specificAttributeNamesAliased.joinToString(", ")
      if (expressionAttributeNames.isNotEmpty()) {
        query.expressionAttributeNames = expressionAttributeNames
      }
    }
    query.withReturnConsumedCapacity(returnConsumedCapacity)
    if (filterExpression != null) {
      query.filterExpression = filterExpression.expression
      query.expressionAttributeValues = filterExpression.attributeValues
    }
    if (initialOffset != null) {
      query.exclusiveStartKey = initialOffset.encodeOffset()
    }
    val page = dynamoDbMapper.queryPage(rawType.java, query)
    val contents = page.results.map { itemCodec.toApp(it) }
    val offset = page.lastEvaluatedKey?.decodeOffset()
    return Page(contents, offset, page.scannedCount, page.consumedCapacity)
  }

  private fun DynamoDBQueryExpression<Any>.apply(keyCondition: KeyCondition<K>) = apply {
    when (keyCondition) {
      is BeginsWith -> {
        val value = keyCodec.toDb(keyCondition.prefix)
        val valueAttributes = tableModel.convert(value)
        val hashKeyValue = tableModel.unconvert(mapOf(hashKeyName to valueAttributes[hashKeyName]))
        withHashKeyValues(hashKeyValue)

        val rangeKeyValue = valueAttributes[rangeKeyName]
        if (rangeKeyValue != null && rangeKeyValue.isNULL != true) {
          withRangeKeyCondition(
            rangeKeyName,
            Condition()
              .withComparisonOperator(BEGINS_WITH)
              .withAttributeValueList(rangeKeyValue)
          )
        }
      }
      is Between -> {
        val start = keyCodec.toDb(keyCondition.startInclusive)
        val end = keyCodec.toDb(keyCondition.endInclusive)
        val startAttributes = tableModel.convert(start)
        val endAttributes = tableModel.convert(end)
        require(startAttributes[hashKeyName] == endAttributes[hashKeyName])
        val hashKeyValue = tableModel.unconvert(mapOf(hashKeyName to startAttributes[hashKeyName]))
        withHashKeyValues(hashKeyValue)
          .withRangeKeyCondition(
            rangeKeyName,
            Condition()
              .withComparisonOperator(BETWEEN)
              .withAttributeValueList(startAttributes[rangeKeyName], endAttributes[rangeKeyName])
          )
      }
    }
  }

  private fun Offset<K>.encodeOffset(): Map<String, AttributeValue> {
    val offsetKey = keyCodec.toDb(key)
    return tableModel.convert(offsetKey)
  }

  private fun Map<String, AttributeValue>.decodeOffset(): Offset<K> {
    val offsetKeyAttributes = tableModel.unconvert(this)
    val offsetKey = keyCodec.toApp(offsetKeyAttributes)
    return Offset(offsetKey)
  }

  companion object {
    private val reservedWords = listOf(
      "ABORT", "ABSOLUTE", "ACTION", "ADD", "AFTER", "AGENT", "AGGREGATE", "ALL", "ALLOCATE",
      "ALTER", "ANALYZE", "AND", "ANY", "ARCHIVE", "ARE", "ARRAY", "AS", "ASC", "ASCII",
      "ASENSITIVE", "ASSERTION", "ASYMMETRIC", "AT", "ATOMIC", "ATTACH", "ATTRIBUTE", "AUTH",
      "AUTHORIZATION", "AUTHORIZE", "AUTO", "AVG", "BACK", "BACKUP", "BASE", "BATCH", "BEFORE",
      "BEGIN", "BETWEEN", "BIGINT", "BINARY", "BIT", "BLOB", "BLOCK", "BOOLEAN", "BOTH",
      "BREADTH", "BUCKET", "BULK", "BY", "BYTE", "CALL", "CALLED", "CALLING", "CAPACITY",
      "CASCADE", "CASCADED", "CASE", "CAST", "CATALOG", "CHAR", "CHARACTER", "CHECK", "CLASS",
      "CLOB", "CLOSE", "CLUSTER", "CLUSTERED", "CLUSTERING", "CLUSTERS", "COALESCE", "COLLATE",
      "COLLATION", "COLLECTION", "COLUMN", "COLUMNS", "COMBINE", "COMMENT", "COMMIT", "COMPACT",
      "COMPILE", "COMPRESS", "CONDITION", "CONFLICT", "CONNECT", "CONNECTION", "CONSISTENCY",
      "CONSISTENT", "CONSTRAINT", "CONSTRAINTS", "CONSTRUCTOR", "CONSUMED", "CONTINUE", "CONVERT",
      "COPY", "CORRESPONDING", "COUNT", "COUNTER", "CREATE", "CROSS", "CUBE", "CURRENT", "CURSOR",
      "CYCLE", "DATA", "DATABASE", "DATE", "DATETIME", "DAY", "DEALLOCATE", "DEC", "DECIMAL",
      "DECLARE", "DEFAULT", "DEFERRABLE", "DEFERRED", "DEFINE", "DEFINED", "DEFINITION", "DELETE",
      "DELIMITED", "DEPTH", "DEREF", "DESC", "DESCRIBE", "DESCRIPTOR", "DETACH", "DETERMINISTIC",
      "DIAGNOSTICS", "DIRECTORIES", "DISABLE", "DISCONNECT", "DISTINCT", "DISTRIBUTE", "DO",
      "DOMAIN", "DOUBLE", "DROP", "DUMP", "DURATION", "DYNAMIC", "EACH", "ELEMENT", "ELSE",
      "ELSEIF", "EMPTY", "ENABLE", "END", "EQUAL", "EQUALS", "ERROR", "ESCAPE", "ESCAPED", "EVAL",
      "EVALUATE", "EXCEEDED", "EXCEPT", "EXCEPTION", "EXCEPTIONS", "EXCLUSIVE", "EXEC", "EXECUTE",
      "EXISTS", "EXIT", "EXPLAIN", "EXPLODE", "EXPORT", "EXPRESSION", "EXTENDED", "EXTERNAL",
      "EXTRACT", "FAIL", "FALSE", "FAMILY", "FETCH", "FIELDS", "FILE", "FILTER", "FILTERING",
      "FINAL", "FINISH", "FIRST", "FIXED", "FLATTERN", "FLOAT", "FOR", "FORCE", "FOREIGN",
      "FORMAT", "FORWARD", "FOUND", "FREE", "FROM", "FULL", "FUNCTION", "FUNCTIONS", "GENERAL",
      "GENERATE", "GET", "GLOB", "GLOBAL", "GO", "GOTO", "GRANT", "GREATER", "GROUP", "GROUPING",
      "HANDLER", "HASH", "HAVE", "HAVING", "HEAP", "HIDDEN", "HOLD", "HOUR", "IDENTIFIED",
      "IDENTITY", "IF", "IGNORE", "IMMEDIATE", "IMPORT", "IN", "INCLUDING", "INCLUSIVE",
      "INCREMENT", "INCREMENTAL", "INDEX", "INDEXED", "INDEXES", "INDICATOR", "INFINITE",
      "INITIALLY", "INLINE", "INNER", "INNTER", "INOUT", "INPUT", "INSENSITIVE", "INSERT",
      "INSTEAD", "INT", "INTEGER", "INTERSECT", "INTERVAL", "INTO", "INVALIDATE", "IS",
      "ISOLATION", "ITEM", "ITEMS", "ITERATE", "JOIN", "KEY", "KEYS", "LAG", "LANGUAGE", "LARGE",
      "LAST", "LATERAL", "LEAD", "LEADING", "LEAVE", "LEFT", "LENGTH", "LESS", "LEVEL", "LIKE",
      "LIMIT", "LIMITED", "LINES", "LIST", "LOAD", "LOCAL", "LOCALTIME", "LOCALTIMESTAMP",
      "LOCATION", "LOCATOR", "LOCK", "LOCKS", "LOG", "LOGED", "LONG", "LOOP", "LOWER", "MAP",
      "MATCH", "MATERIALIZED", "MAX", "MAXLEN", "MEMBER", "MERGE", "METHOD", "METRICS", "MIN",
      "MINUS", "MINUTE", "MISSING", "MOD", "MODE", "MODIFIES", "MODIFY", "MODULE", "MONTH",
      "MULTI", "MULTISET", "NAME", "NAMES", "NATIONAL", "NATURAL", "NCHAR", "NCLOB", "NEW",
      "NEXT", "NO", "NONE", "NOT", "NULL", "NULLIF", "NUMBER", "NUMERIC", "OBJECT", "OF",
      "OFFLINE", "OFFSET", "OLD", "ON", "ONLINE", "ONLY", "OPAQUE", "OPEN", "OPERATOR", "OPTION",
      "OR", "ORDER", "ORDINALITY", "OTHER", "OTHERS", "OUT", "OUTER", "OUTPUT", "OVER",
      "OVERLAPS", "OVERRIDE", "OWNER", "PAD", "PARALLEL", "PARAMETER", "PARAMETERS", "PARTIAL",
      "PARTITION", "PARTITIONED", "PARTITIONS", "PATH", "PERCENT", "PERCENTILE", "PERMISSION",
      "PERMISSIONS", "PIPE", "PIPELINED", "PLAN", "POOL", "POSITION", "PRECISION", "PREPARE",
      "PRESERVE", "PRIMARY", "PRIOR", "PRIVATE", "PRIVILEGES", "PROCEDURE", "PROCESSED",
      "PROJECT", "PROJECTION", "PROPERTY", "PROVISIONING", "PUBLIC", "PUT", "QUERY", "QUIT",
      "QUORUM", "RAISE", "RANDOM", "RANGE", "RANK", "RAW", "READ", "READS", "REAL", "REBUILD",
      "RECORD", "RECURSIVE", "REDUCE", "REF", "REFERENCE", "REFERENCES", "REFERENCING", "REGEXP",
      "REGION", "REINDEX", "RELATIVE", "RELEASE", "REMAINDER", "RENAME", "REPEAT", "REPLACE",
      "REQUEST", "RESET", "RESIGNAL", "RESOURCE", "RESPONSE", "RESTORE", "RESTRICT", "RESULT",
      "RETURN", "RETURNING", "RETURNS", "REVERSE", "REVOKE", "RIGHT", "ROLE", "ROLES", "ROLLBACK",
      "ROLLUP", "ROUTINE", "ROW", "ROWS", "RULE", "RULES", "SAMPLE", "SATISFIES", "SAVE",
      "SAVEPOINT", "SCAN", "SCHEMA", "SCOPE", "SCROLL", "SEARCH", "SECOND", "SECTION", "SEGMENT",
      "SEGMENTS", "SELECT", "SELF", "SEMI", "SENSITIVE", "SEPARATE", "SEQUENCE", "SERIALIZABLE",
      "SESSION", "SET", "SETS", "SHARD", "SHARE", "SHARED", "SHORT", "SHOW", "SIGNAL", "SIMILAR",
      "SIZE", "SKEWED", "SMALLINT", "SNAPSHOT", "SOME", "SOURCE", "SPACE", "SPACES", "SPARSE",
      "SPECIFIC", "SPECIFICTYPE", "SPLIT", "SQL", "SQLCODE", "SQLERROR", "SQLEXCEPTION",
      "SQLSTATE", "SQLWARNING", "START", "STATE", "STATIC", "STATUS", "STORAGE", "STORE",
      "STORED", "STREAM", "STRING", "STRUCT", "STYLE", "SUB", "SUBMULTISET", "SUBPARTITION",
      "SUBSTRING", "SUBTYPE", "SUM", "SUPER", "SYMMETRIC", "SYNONYM", "SYSTEM", "TABLE",
      "TABLESAMPLE", "TEMP", "TEMPORARY", "TERMINATED", "TEXT", "THAN", "THEN", "THROUGHPUT",
      "TIME", "TIMESTAMP", "TIMEZONE", "TINYINT", "TO", "TOKEN", "TOTAL", "TOUCH", "TRAILING",
      "TRANSACTION", "TRANSFORM", "TRANSLATE", "TRANSLATION", "TREAT", "TRIGGER", "TRIM", "TRUE",
      "TRUNCATE", "TTL", "TUPLE", "TYPE", "UNDER", "UNDO", "UNION", "UNIQUE", "UNIT", "UNKNOWN",
      "UNLOGGED", "UNNEST", "UNPROCESSED", "UNSIGNED", "UNTIL", "UPDATE", "UPPER", "URL", "USAGE",
      "USE", "USER", "USERS", "USING", "UUID", "VACUUM", "VALUE", "VALUED", "VALUES", "VARCHAR",
      "VARIABLE", "VARIANCE", "VARINT", "VARYING", "VIEW", "VIEWS", "VIRTUAL", "VOID", "WAIT",
      "WHEN", "WHENEVER", "WHERE", "WHILE", "WINDOW", "WITH", "WITHIN", "WITHOUT", "WORK",
      "WRAPPED", "WRITE", "YEAR", "ZONE"
    )
  }
}
