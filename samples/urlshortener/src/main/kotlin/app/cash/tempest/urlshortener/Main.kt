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

package app.cash.tempest.urlshortener

import app.cash.tempest.LogicalDb
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

lateinit var urlShortener: UrlShortener

class Cli : CliktCommand(help = "Configure access key and region environment variables using https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-envvars.html") {

  override fun run() {
    val client = AmazonDynamoDBClientBuilder.standard().build()
    val mapper = DynamoDBMapper(client)
    val db = LogicalDb<AliasDb>(mapper)
    urlShortener = RealUrlShortener(db.aliasTable)
  }
}

class Shorten : CliktCommand(help = "Creates a custom alias from `shortUrl` to `destinationUrl`") {
  val shortUrl: String by option(help = "Short URL").required()
  val destinationUrl: String by option(help = "Destination URL").required()

  override fun run() {
    urlShortener.shorten(shortUrl, destinationUrl)
    echo("Successful")
  }
}

class Redirect : CliktCommand(help = "Redirects `shortUrl` to its destination") {
  val shortUrl: String by option(help = "Short URL").required()

  override fun run() {
    val destinationUrl = urlShortener.redirect(shortUrl)
    echo("$destinationUrl")
  }
}

fun main(args: Array<String>) = Cli().subcommands(Shorten(), Redirect()).main(args)
