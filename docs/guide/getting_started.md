!!! tip
    In DynamoDB, [tables, items, and attributes](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.CoreComponents.html) are the core components that you work with. 
    A table is a collection of items, and each item is a collection of attributes. 
    DynamoDB uses primary keys to uniquely identify each item in a table and secondary indexes to provide more querying flexibility.

    To learn more about DynamoDB, check out the [official developer guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Introduction.html).

## Get Tempest

First, add Tempest to your project.

With Gradle:

```groovy
dependencies {
  implementation "app.cash.tempest:tempest:{{ versions.tempest }}"
}
```

## Start Coding

Let's build a URL shortener with the following features:

* Creating custom aliases from a short URL to a destination URL.
* Redirecting existing short URLs to destination URLs.  

We express it like this in Kotlin.
```kotlin
interface UrlShortener {
  /** 
   * Creates a custom alias from [shortUrl] to [destinationUrl]. 
   * @return false if [shortUrl] is taken. 
   */
  fun shorten(shortUrl: String, destinationUrl: String): Boolean
  
  /** 
    * Redirects [shortUrl] to its destination. 
    * @return null if not found. 
    */
  fun redirect(shortUrl: String): String?
}
```

We will store URL aliases in the following table.

<table>
  <tbody>
    <tr>
      <td colspan=1 align="center">Primary Key</td>
      <td rowspan=2 colspan=1 align="center" valign="top">Attributes</td>
    </tr>
    <tr>
      <td><strong>short_url</strong></td>
    </tr>
    <tr>
      <!-- Note: It is important to declare both vertical-align and valign here. 
           vertical-align only works in the project website 
           while valign only works in Github's formatting for README.md. -->
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">SquareCLA</td>
      <td><strong>destination_url</strong></td>
    </tr>
    <tr>
      <td>https://docs.google.com/forms/d/e/1FAIpQLSeRVQ35-gq2vdSxD1kdh7CJwRdjmUA0EZ9gRXaWYoUeKPZEQQ/viewform?formkey=dDViT2xzUHAwRkI3X3k5Z0lQM091OGc6MQ&ndplr=1</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">KindleWireless</td>
      <td><strong>destination_url</strong></td>
    </tr>
    <tr>
      <td>http://www.amazon.com/Kindle-Wireless-Reading-Display-Globally/dp/B003FSUDM4/ref=amb_link_353259562_2?pf_rd_m=ATVPDKIKX0DER&pf_rd_s=center-10&pf_rd_r=11EYKTN682A79T370AM3&pf_rd_t=201&pf_rd_p=1270985982&pf_rd_i=B002Y27P3M</td>
    </tr>
    <tr>
      <td rowspan=2 style="vertical-align:bottom;" valign="bottom">BestUrlShortener</td>
      <td><strong>destination_url</strong></td>
    </tr>
    <tr>
      <td>https://www.google.com/search?q=best+url+shortener&oq=best+url+shortener&aqs=chrome..69i57j69i64l2.8705j0j1&sourceid=chrome&ie=UTF-8</td>
    </tr>
    <tr>
      <td colspan=2>...</td>
    </tr>    
  </tbody>
</table>

To access this table in Kotlin, model it using [`DynamoDBMapper`](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.html).

```kotlin
// Note: this POJO is not type-safe because its attributes are nullable and mutable.
@DynamoDBTable(tableName = "alias_items")
class AliasItem {
  @DynamoDBHashKey
  var short_url: String? = null
  @DynamoDBAttribute
  var destination_url: String? = null
}
```

Tempest lets you interact with `AliasItem` using strongly typed data classes.
  
```kotlin
interface UrlShortenerDb: LogicalDb {
  val aliasTable: AliasTable
}

interface AliasTable : LogicalTable<AliasItem> {
  val aliases: InlineView<Alias.Key, Alias>
}

data class Alias(
  val short_url: String,
  val destination_url: String
) {
  data class Key(
    val short_url: String 
  )
}
```

Let's put everything together.
          
```kotlin
class RealUrlShortener(
  private val table: AliasTable
): UrlShortener {

  override fun shorten(shortUrl: String, destinationUrl: String): Boolean {
    val item = Alias(shortUrl, destinationUrl)
    val ifNotExist = DynamoDBSaveExpression()
      .withExpectedEntry("short_url", ExpectedAttributeValue().withExists(false))
    return try {
      table.aliases.save(item, ifNotExist)
      true
    } catch (e: ConditionalCheckFailedException) {
      println("Failed to shorten $shortUrl because it already exists!")
      false
    }
  }
  
  override fun redirect(shortUrl: String): String? {
    val key = Alias.Key(shortUrl)
    return table.aliases.load(key)?.destination_url
  }
}

fun main() {
  val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard().build()
  val mapper: DynamoDBMapper = DynamoDBMapper(client)
  val db: UrlShortenerDb = LogicalDb(mapper)
  val urlShortener = RealUrlShortener(db.aliasTable)
  urlShortener.shorten("tempest", "https://cashapp.github.io/tempest")
}
```
