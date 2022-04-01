# Change Log

## [1.6.1] - 2022-04.01

* Fixed: Fixing release logic (#99)

## [1.6.0] - 2022-03-30

* New: Adds the correct sqlite4java dep when running on M1 Macs (#96).
* Fixed: unit test to be able to run in docker bridge environment (#97).
* Fixed: need to allow m1 arch (#94).
* Fixed: can't do enforce of the platform (#93)
* Fixed: Improve error message about property names starting with is (#71)
* Fixed: syntax formatting in README.md (#69)
* Fixed: typo 'album_artiest' => 'album_artist' (#68)
* Fixed: Improve performance for cache reflection results (#65)
* Chore: Upgrade to kotlin 1.6; gradle 7.3.2; fix docker usage (#92)
* Chore: Bump actions/checkout from 2.3.4 to 3 dependencies github_actions (#90)
* Chore: Update log4j version to 2.16.0. (#85)
* Chore: Upgrade spotless to a 5.x version (#84)
* Chore: Upgrade log4j version to avoid potential RCE vuln (#83)
* Chore: Bump junixsocket-common from 2.3.4 to 2.4.0  dependencies java (#67)
* Chore: Bump junixsocket-native-common from 2.3.4 to 2.4.0  dependencies java (#66)

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
