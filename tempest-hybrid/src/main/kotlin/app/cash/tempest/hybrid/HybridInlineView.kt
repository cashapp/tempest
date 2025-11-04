/*
 * Copyright 2024 Square Inc.
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
package app.cash.tempest.hybrid

import app.cash.tempest.InlineView

/**
 * Marker interface for hybrid views that support transparent S3 hydration.
 *
 * This interface is used internally by the proxy system to identify views
 * that have been wrapped with hybrid functionality.
 */
interface HybridInlineView<K : Any, I : Any> : InlineView<K, I> {
  /**
   * Load an item, automatically hydrating from S3 if it's been archived.
   * This method provides the same functionality as the regular load() but
   * explicitly indicates hybrid support.
   */
  fun loadHybrid(key: K): I?
}