!!! tip "Prerequisites"
    In DynamoDB, [tables, items, and attributes](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.CoreComponents.html) are the core components that you work with. 
    A table is a collection of items, and each item is a collection of attributes. 
    DynamoDB uses primary keys to uniquely identify each item in a table and secondary indexes to provide more querying flexibility.

    To learn more about DynamoDB, check out the [official developer guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Introduction.html).

## Get Tempest

First, add Tempest to your project.

For AWS SDK 1.x:

```groovy
dependencies {
  implementation "app.cash.tempest:tempest:{{ versions.tempest }}"
}
```

For AWS SDK 2.x:

```groovy
dependencies {
  implementation "app.cash.tempest:tempest2:{{ versions.tempest }}"
}
```

## Start Coding

Let's build a URL shortener with the following features:

* Creating custom aliases from a short URL to a destination URL.
* Redirecting existing short URLs to destination URLs.  

We express it like this in code.

=== "Kotlin"

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

=== "Java"

    ```java
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

To access this table in code, model it using 
[`DynamoDBMapper`](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.html) or 
[`DynamoDbEnhancedClient`](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/enhanced/dynamodb/DynamoDbEnhancedClient.html).

> Note: The base item type `AliasItem` is still used for the `LogicalTable`. This type is intended to model an empty row, so all its fields should be nullable with a `null` default value. Using non-nullable types or fields with default values will cause issues during serialization and querying.

=== "Kotlin - SDK 2.x"

    ```kotlin
    // Note: this POJO is not type-safe because its attributes are nullable and mutable.
    @DynamoDbBean
    class AliasItem {
      @get:DynamoDbPartitionKey
      var short_url: String? = null
      var destination_url: String? = null
    }
    ```

=== "Java - SDK 2.x"

    ```java
    // Note: this POJO is not type-safe because its attributes are nullable and mutable.
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
    ```

=== "Kotlin - SDK 1.x"

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

=== "Java - SDK 1.x"

    ```java
    // Note: this POJO is not type-safe because its attributes are nullable and mutable.
    @DynamoDBTable(tableName = "alias_items")
    public class AliasItem {
      private String shortUrl;
      private String destinationUrl;

      @DynamoDBHashKey(attributeName = "short_url")
      public String getShortUrl() {
        return shortUrl;
      }

      public void setShortUrl(String short_url) {
        this.shortUrl = short_url;
      }

      @DynamoDBAttribute(attributeName = "destination_url")
      public String getDestinationUrl() {
        return destinationUrl;
      }

      public void setDestinationUrl(String destination_url) {
        this.destinationUrl = destination_url;
      }
    }
    ```

Tempest lets you interact with `AliasItem` using strongly typed data classes.
  
=== "Kotlin - SDK 2.x"

    ```kotlin
    interface AliasDb : LogicalDb {
      @TableName("alias_items")
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

=== "Java - SDK 2.x"

    ```java
    public interface AliasDb extends LogicalDb {
      @TableName("alias_items")
      AliasTable aliasTable();
    }

    public interface AliasTable extends LogicalTable<AliasItem> {
      InlineView<Alias.Key, Alias> aliases();
    }

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
    ```

=== "Kotlin - SDK 1.x"

    ```kotlin
    interface AliasDb: LogicalDb {
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

=== "Java - SDK 1.x"

    ```java
    public interface AliasDb extends LogicalDb {
      AliasTable aliasTable();
    }

    public interface AliasTable extends LogicalTable<AliasItem> {
      InlineView<Alias.Key, Alias> aliases();
    }

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
    ```

Let's put everything together.

=== "Kotlin - SDK 2.x"

    ```kotlin
    class RealUrlShortener(
      private val table: AliasTable
    ) : UrlShortener {
    
      override fun shorten(shortUrl: String, destinationUrl: String): Boolean {
        val item = Alias(shortUrl, destinationUrl)
        val ifNotExist = Expression.builder()
          .expression("attribute_not_exists(short_url)")
          .build()
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
    
    fun main(args: Array<String>) {
      val client = DynamoDbEnhancedClient.create()
      val db = LogicalDb<AliasDb>(client)
      urlShortener = RealUrlShortener(db.aliasTable)
      urlShortener.shorten("tempest", "https://cashapp.github.io/tempest")
    }
    ```

=== "Java - SDK 2.x"

    ```java
    public class RealUrlShortener implements UrlShortener {
    
      private final AliasTable table;
    
      public RealUrlShortener(AliasTable table) {
        this.table = table;
      }
    
      @Override
      public boolean shorten(String shortUrl, String destinationUrl) {
        Alias item = new Alias(shortUrl, destinationUrl);
        Expression ifNotExist = Expression.builder()
            .expression("attribute_not_exists(short_url)")
            .build();
        try {
          table.aliases().save(item, ifNotExist);
          return true;
        } catch (ConditionalCheckFailedException e) {
          System.out.println("Failed to shorten $shortUrl because it already exists!");
          return false;
        }
      }
    
      @Override
      @Nullable
      public String redirect(String shortUrl) {
        Alias.Key key = new Alias.Key(shortUrl);
        Alias alias = table.aliases().load(key);
        if (alias == null) {
          return null;
        }
        return alias.destination_url;
      }
    }
    
    public static void main(String[] args) {
      DynamoDbEnhancedClient client = DynamoDbEnhancedClient.create();
      AliasDb db = LogicalDb.create(AliasDb.class, client);
      UrlShortener urlShortener = new RealUrlShortener(db.aliasTable());
      urlShortener.shorten("tempest", "https://cashapp.github.io/tempest");
    }
    ```

=== "Kotlin - SDK 1.x"
   
    ```kotlin
    class RealUrlShortener(
      private val table: AliasTable
    ) : UrlShortener {
    
      override fun shorten(shortUrl: String, destinationUrl: String): Boolean {
        val item = Alias(shortUrl, destinationUrl)
        val ifNotExist = DynamoDBSaveExpression()
          .withExpectedEntry("short_url", ExpectedAttributeValue()
            .withExists(false))
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
    
    fun main(args: Array<String>) {
      val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard().build()
      val mapper: DynamoDBMapper = DynamoDBMapper(client)
      val db: AliasDb = LogicalDb(mapper)
      val urlShortener = RealUrlShortener(db.aliasTable)
      urlShortener.shorten("tempest", "https://cashapp.github.io/tempest")
    }
    ```

=== "Java - SDK 1.x"

    ```java
    public class RealUrlShortener implements UrlShortener {
    
      private final AliasTable table;
    
      public RealUrlShortener(AliasTable table) {
        this.table = table;
      }
    
      @Override
      public boolean shorten(String shortUrl, String destinationUrl) {
        Alias item = new Alias(shortUrl, destinationUrl);
        DynamoDBSaveExpression ifNotExist = new DynamoDBSaveExpression()
            .withExpectedEntry(
                "short_url",
                new ExpectedAttributeValue().withExists(false));
        try {
          table.aliases().save(item, ifNotExist);
          return true;
        } catch (ConditionalCheckFailedException e) {
          System.out.println("Failed to shorten $shortUrl because it already exists!");
          return false;
        }
      }
    
      @Override
      @Nullable
      public String redirect(String shortUrl) {
        Alias.Key key = new Alias.Key(shortUrl);
        Alias alias = table.aliases().load(key);
        if (alias == null) {
          return null;
        }
        return alias.destination_url;
      }
    }

    public static void main(String[] args) {
      AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
      DynamoDBMapper mapper = new DynamoDBMapper(client);
      AliasDb db = LogicalDb.create(AliasDb.class, mapper);
      UrlShortener urlShortener = new RealUrlShortener(db.aliasTable());
      urlShortener.shorten("tempest", "https://cashapp.github.io/tempest");
    }
    ```

---

Check out the code samples on Github:

 * URL Shortener - SDK 1.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/urlshortener/src/main/kotlin/app/cash/tempest/urlshortener), [.java](https://github.com/cashapp/tempest/tree/main/samples/urlshortener/src/main/java/app/cash/tempest/urlshortener/java))
 * URL Shortener - SDK 2.x ([.kt](https://github.com/cashapp/tempest/tree/main/samples/urlshortener2/src/main/kotlin/app/cash/tempest2/urlshortener), [.java](https://github.com/cashapp/tempest/tree/main/samples/urlshortener2/src/main/java/app/cash/tempest2/urlshortener/java))
