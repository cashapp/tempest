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

package app.cash.tempest.interop;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "j_alias_items")
public class JAliasItem {
  private String short_url;
  private String destination_url;

  @DynamoDBHashKey(attributeName = "short_url")
  public String getShortUrl() {
    return short_url;
  }

  public void setShortUrl(String short_url) {
    this.short_url = short_url;
  }

  @DynamoDBAttribute(attributeName = "destination_url")
  public String getDestinationUrl() {
    return destination_url;
  }

  public void setDestinationUrl(String destination_url) {
    this.destination_url = destination_url;
  }

}
