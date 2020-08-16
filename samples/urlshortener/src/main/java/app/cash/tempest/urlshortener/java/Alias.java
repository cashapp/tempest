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

public class Alias {

  public final String short_url;
  public final String destination_url;

  public Alias(String short_url, String destination_url) {
    this.short_url = short_url;
    this.destination_url = destination_url;
  }

  public Key key() {
    return new Key(short_url);
  }

  public static class Key {

    public final String short_url;

    public Key(String short_url) {
      this.short_url = short_url;
    }
  }
}
