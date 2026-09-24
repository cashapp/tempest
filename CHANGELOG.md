# Change Log

> Starting in 2024, releases use a date-based version format
> (`vYYYY.MM.DD.HHMMSS-<sha>`) instead of semantic versioning. Each merge to
> main is automatically published as a new release.

## [v2026.01.21.182711-d04106f] - 2026-01-21
* New: Add `put()` method to `TransactionWriteSet` (#246)

## [v2025.11.04.000323-3c7c6c2] - 2025-11-04
* Fix: Only create lookup if consistency level is different (#243)

## [v2025.10.27.160126-16a22a2] - 2025-10-27
* Fix: `MethodHandle` creation should use table class (#242)

## [v2025.10.23.165355-269d2e7] - 2025-10-23
* Chore: Update amazon/dynamodb-local images to match embedded version (#241)

## [v2025.10.21.114346-63954e1] - 2025-10-21
* Chore: Use `BeanTableSchemaParams` in `TableSchemaFactory` (#240)

## [v2025.09.18.063705-9bd8953] - 2025-09-18
* Chore: Upgrade most dependencies (#239)

## [v2025.07.17.171007-3fc2a07] - 2025-07-17
* New: Extend `WritingPager` to work with `AsyncLogicalDb` (#236)

## [v2025.07.15.155806-c1415e3] - 2025-07-15
* Fix: `TempestDateAttributeConverter` dropping millisecond precision (#237)

## [v2025.07.08.171526-e297588] - 2025-07-08
* Fix: Null `AttributeValue` preventing `CreatedAt` timestamps during `UpdateItem` (#235)

## [v2025.06.11.195708-6267180] - 2025-06-11
* Chore: Migrate to Sonatype Central Portal (#233)

## [v2025.06.03.162437-8f485e7] - 2025-06-03
* New: Support `UpdatedAt` and `CreatedAt` timestamps (#234)

## [v2025.05.08.201657-53a060d] - 2025-05-08
* New: Support updated item on save with custom `WithResultExtension` (#221)

## [v2025.05.06.204520-5d19a8b] - 2025-05-06
* Chore: Relocate the remaining two packages from the dynamodb local jar (#231)

## [v2025.05.02.195945-d393c44] - 2025-05-02
* Chore: Update docker-java version to latest available (#224)

## [v2025.05.01.210208-8bac78f] - 2025-05-01
* Chore: Simplify shadowJar publishing (#230)

## [v2025.04.30.143145-e1d5ea6] - 2025-04-30
* Chore: Upgrade Gradle to v8.14 (#229)

## [v2025.04.29.222809-664e313] - 2025-04-29
* Chore: Shade ANTLR in tempest-dynamodb-local (#228)

## [v2025.04.29.195656-416a281] - 2025-04-29
* Chore: Switch from org.json to Jackson to preserve property ordering (#227)

## [v2025.04.29.173320-b9a022f] - 2025-04-29
* Chore: Post-process the Gradle module metadata to use the shadow jar deps (#226)

## [v2025.04.29.013352-ef84c17] - 2025-04-29
* Chore: Use the shadow plugin to shade Jetty (#225)

## [v2025.04.17.121601-f8f94b6] - 2025-04-17
* Chore: Upgrade AWS DynamoDB Local to v2.6.0 (#222)

## [v2025.03.17.133301-6c83654] - 2025-03-17
* New: Add helper class to fetch docker registry credentials (#220)

## [v2024.12.17.173224-cec84f6] - 2024-12-17
* Chore: Bump org.junit.jupiter:junit-jupiter-engine from 5.8.2 to 5.11.4 (#204)

## [v2024.11.21.183936-989ef8d] - 2024-11-21
* Docs: Remove absolute path

## [v2024.11.21.183107-0022dfb] - 2024-11-21
* Docs: Fix upgrade guide links (#194)

## [v2024.11.21.173322-f191626] - 2024-11-21
* Docs: Fix broken docs sidebar links (#193)

## [v2024.11.05.180436-5071a87] - 2024-11-05
* Fix: Make the aarch64 lib dependency unconditional (#192)

## [v2024.09.04.165019-8430cf3] - 2024-09-04
* New: Introduce support for parallel scans into tempest2 (#190)

## [v2024.09.03.221103-9c4094e] - 2024-09-03
* Fix: Pass `consistentReads` through overload (#191)

## [v2024.08.07.002316-64f40ef] - 2024-08-07
* New: Add method to test DDB clients for resetting tables in between tests (#189)

## [v2024.07.09.154025-e7197de] - 2024-07-09
* Fix: Handle network removal gracefully (#188)

## [v2024.06.06.210244-397e6af] - 2024-06-06
* Fix: Handle unprocessed deletes without throwing an `IllegalArgumentException` (#187)

## [v2024.06.03.153616-2a33625] - 2024-06-03
* Fix: Try to close reused sockets in `TestDynamoDbService` (#185)

## [v2024.05.31.162126-4b6b047] - 2024-05-31
* Fix: Hold onto allocated ports to avoid port conflicts in concurrent tests (tempest2) (#184)

## [v2024.05.30.154311-38c0913] - 2024-05-30
* Fix: Hold onto randomly allocated ports to avoid port conflicts in concurrent tests (#182)

## [v2024.05.02.220037-0c53a3f] - 2024-05-02
* New: Add support for query all and scan all (#180)

## [v2024.05.02.204603-057472e] - 2024-05-02
* New: Add `TableNameResolver` to tempest2 (#179)

## [v2024.04.03.173818-899f19f] - 2024-04-03
* Chore: Set `-Xjvm-default=all-compatibility` compiler arg (#178)

## [v2024.04.02.213403-4170185] - 2024-04-02
* Chore: Bump to Java 11 (#177)

## [v2024.04.02.174633-95a97b2] - 2024-04-02
* Fix: Consume all result pages in `DynamoDbLogicalDb.batchLoad` (#176)

## [v2024.03.25.180845-91fd675] - 2024-03-25
* Chore: Bump to Kotlin 1.9.23, Dokka 1.9.20 (#175)

## [v2024.03.22.175754-781d9c5] - 2024-03-22
* New: Add APIs to execute batch, query and load requests along with consumed capacity (#173)
* Chore: Rollback Kotlin to 1.8.22 (#174)

## [v2024.03.07.210049-c13b682] - 2024-03-07
* New: Make the secondary index range key nullable (#170)

## [v2024.01.31.165844-7907db0] - 2024-01-31
* Chore: Cleanup old CI and docs (#169)

## [v2024.01.25.185541-59169c5] - 2024-01-25
* Chore: Migrate to `libs.versions.toml` (#165)
* Chore: Copy CI publish auto-release from Misk (#166)
* Chore: Fix CI (#167, #168)

## [1.10.3] - 2023-12-15
* Fix: Pin DynamoDB Docker image to 2.1.0 to avoid breaking issue with latest

## [1.10.2] - 2023-08-16
* Fix: Creating new tag for publication

## [1.10.1] - 2023-08-16
* Fix: Initialize the dynamodb clients immediately to avoid DI issues in consumers

## [1.10.0] - 2023-06-06
* Fix: Async Batch support for multiple pages

## [1.9.0] - 2023-06-06
* New: add BOM

## [1.8.0] - 2023-05-31
* New(tempest2): Adds `Attribute.allowEmpty` to support nullable, prefixed fields

## [1.7.0] - 2023-05-03

* New(gradle): Add Hermit
* New: Add `pageWritten` hook to `WritingPager.Handler`
* New: Add paging to `batchLoad` and `batchWrite` (sync only)
* Docs: Migration guide for v1 -> v2
* Test: Shorter timeout on test dynamo server
* Chore(deps): Upgrade OpenJDK to 17
* Chore(gradle): Upgrade to 7.6.1
* Chore(deps): Upgrade to Kotlin 1.7
* Chore(gradle): Swap out kotlin-dsl for kotlin-jvm

## [1.6.2] - 2022-04-01

* Fixed: Fixing release, making sure we run the publication logic (#100)

## [1.6.1] - 2022-04-01

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
