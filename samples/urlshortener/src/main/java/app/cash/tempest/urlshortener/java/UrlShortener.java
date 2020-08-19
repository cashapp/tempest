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

package app.cash.tempest.urlshortener.java;

import javax.annotation.Nullable;

public interface UrlShortener {
  /**
   * Creates a custom alias from {@code shortUrl} to {@code destinationUrl}.
   * @return false if {@code shortUrl} is taken.
   */
  boolean shorten(String shortUrl, String destinationUrl);

  /**
   * Redirects {@code shortUrl} to its destination.
   * @return null if not found.
   */
  @Nullable
  String redirect(String shortUrl);
}
