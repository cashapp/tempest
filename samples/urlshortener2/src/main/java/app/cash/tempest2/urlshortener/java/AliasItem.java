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

package app.cash.tempest2.urlshortener.java;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class AliasItem {
  private String short_url;
  private String destination_url;

  @DynamoDbPartitionKey
  @DynamoDbAttribute("short_url")
  public String getShortUrl() {
    return short_url;
  }

  public void setShortUrl(String short_url) {
    this.short_url = short_url;
  }

  @DynamoDbAttribute("destination_url")
  public String getDestinationUrl() {
    return destination_url;
  }

  public void setDestinationUrl(String destination_url) {
    this.destination_url = destination_url;
  }

}
