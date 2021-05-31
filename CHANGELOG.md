# Change Log

## [1.5.2] - 2021-05-31

* Fixed: Make it easy to integrate with other testing frameworks (#58).

## [1.5.1] - 2021-05-31

* Fixed: To share one test server with multiple tests (#57).

## [1.5.0] - 2021-05-28

* New: Add async and suspending APIs for SDK 2.x (#48)
* New: In SDK 1.x, save now returns a copy of locally updated attributes (#55).

## [1.4.1] - 2021-04-22

* Fixed: Improve error message (#44)
* Fixed: Improve documentation (#43, #46).

## [1.4.0] - 2021-03-08

* New: `tempest-testing` APIs for testing DynamoDB clients using DynamoDBLocal (#33).
* New: Support for Java Record (#27).

## [1.3.0] - 2021-01-22

* New: `app.cash.tempest:tempest2:1.3.0` supports AWS SDK 2.x (#23).

## [1.2.1] - 2020-12-09

* Fixed: Automatically handle reserved words (#20).

## [1.2.0] - 2020-11-20

* Fixed: Remove `IllegalStateException` wrapping of `TransactionCanceledException` (#18).

## [1.1.0] - 2020-11-18

* New: `LogicalTable.codec` makes it easy to interoperate with `DynamoDBMapper` APIs.

## [1.0.1] - 2020-09-18

* New: Make `Offset`'s constructor public.

## [1.0.0] - 2020-08-21

* New: Add Java friendly APIs.
* New: Support item types and key types declared in Java.
* New: Ignore transient properties in item types and key types.

## [0.1.0] - 2020-07-06

Initial release.
