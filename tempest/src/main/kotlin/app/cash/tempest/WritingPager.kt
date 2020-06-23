/*
 * Copyright 2020 Square Inc.
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

package app.cash.tempest

/**
 * A control flow abstraction for paging transactional writes.
 */
class WritingPager<T>(
  private val db: LogicalDb,
  private val maxTransactionItems: Int,
  private val handler: Handler<T>,
  private val updates: List<T>
) {
  /** The number of updates successfully applied. */
  var updatedCount = 0
    private set

  /** A snapshot of the elements yet to be updated. */
  val remainingUpdates: List<T>
    get() = updates.subList(updatedCount, updates.size)

  fun execute() {
    while (updatedCount < updates.size) {
      handler.eachPage {
        val pageSize = updatePage()
        updatedCount += pageSize
      }
    }
  }

  /** Returns the number of entities that were updated. */
  private fun updatePage(): Int {
    check(remainingUpdates.isNotEmpty())

    val currentPageSize = handler.beforePage(remainingUpdates, maxTransactionItems)
    val currentPage = remainingUpdates.take(currentPageSize)

    val writeSet = TransactionWriteSet.Builder()
    val appliedUpdates = mutableListOf<T>()

    while (appliedUpdates.size < currentPage.size &&
        writeSet.size <= maxTransactionItems) {
      val newEntity = currentPage[appliedUpdates.size]

      val itemWriteSet = TransactionWriteSet.Builder()
      handler.item(itemWriteSet, newEntity)

      if (writeSet.size + itemWriteSet.size > maxTransactionItems) {
        break // This item would have caused us to exceed the page limit. Skip it.
      }

      writeSet.addAll(itemWriteSet)
      appliedUpdates += newEntity
    }

    if (appliedUpdates.isEmpty()) {
      return 0 // Not updating any sync entities. Discard the transaction.
    }

    handler.finishPage(writeSet)
    check(writeSet.size <= maxTransactionItems) { "finishPage wrote too many items" }

    db.transactionWrite(writeSet.build())

    return appliedUpdates.size
  }

  interface Handler<T> {
    /**
     * Intercept each page's processing. Use this to decorate processing with metrics or retries.
     */
    fun eachPage(proceed: () -> Unit)

    /**
     * Invoked before each page with the full set of updates yet be processed.
     *
     * @param remainingUpdates all remaining updates. This may be more than a single page of
     *     entities.
     * @return the number of updates that fits in the current page.
     */
    fun beforePage(remainingUpdates: List<T>, maxTransactionItems: Int): Int

    /**
     * Invoked to update each item.
     */
    fun item(builder: TransactionWriteSet.Builder, item: T)

    /**
     * Invoked after a page of items has been computed.
     */
    fun finishPage(builder: TransactionWriteSet.Builder)
  }
}

fun <DB : LogicalDb, T> DB.transactionWritingPager(
  items: List<T>,
  maxTransactionItems: Int = 25,
  handler: WritingPager.Handler<T>
): WritingPager<T> {
  return WritingPager(
      db = this,
      maxTransactionItems = maxTransactionItems,
      updates = items,
      handler = handler
  )
}
