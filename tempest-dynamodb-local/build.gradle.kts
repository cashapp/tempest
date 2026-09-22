import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.gradleup.shadow")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  // Ignore transitive dependencies and instead manage explicitly.
  implementation(libs.awsDynamodbLocal) {
    isTransitive = false
  }
  implementation(libs.partiqlLang) {
    isTransitive = false
  }
  implementation(libs.partiqlIrGeneratorRuntime) {
    isTransitive = false
  }

  // Implementation dependencies will be shaded in the JAR.
  implementation(libs.bundles.jackson)
  implementation(libs.bundles.jetty)
  implementation(libs.antlr4Runtime)
  implementation(libs.jodaTime)
  implementation(libs.kotlinStdLib)

  // Shadow dependencies will not be shaded.
  // https://gradleup.com/shadow/configuration/#configuring-the-runtime-classpath
  shadow(libs.bundles.sqlite4java)
  shadow(libs.aws2Dynamodb)
  shadow(libs.aws2DynamodbEnhanced)
  shadow(libs.aws2Pinpoint)
  shadow(libs.aws2CognitoIdentity)
  shadow(libs.aws2CognitoIdentityProvider)
  shadow(libs.aws2UrlConnectionClient)
  shadow(libs.commonsCli)
  shadow(libs.commonsLang3)
  shadow(libs.commonsLogging)
  shadow(libs.jakartaTransactionApi)
  shadow(libs.guava)
  shadow(libs.slf4jApi)
  shadow(libs.log4jApi)
  shadow(libs.log4jCore)
}

tasks.jar {
  archiveClassifier.set("unshaded")
}

tasks.shadowJar {
  // https://gradleup.com/shadow/configuration/reproducible-builds/
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true

  // Explicit allow-list of dependencies to be included. Without this block, _everything_ on runtimeClasspath would be
  // included. An alternative would be an explicit deny-list, but this is seen as safer. Engineers should take care to
  // update it when they update the dependencies of this project.
  dependencies {
    include(dependency("software.amazon.dynamodb:DynamoDBLocal"))
    include(dependency("com.fasterxml.jackson.core:.*"))
    include(dependency("com.fasterxml.jackson.dataformat:.*"))
    include(dependency("com.fasterxml.jackson.datatype:.*"))
    include(dependency("com.fasterxml.jackson.module:.*"))
    include(dependency("org.antlr:.*"))
    include(dependency("org.eclipse.jetty:.*"))
    include(dependency("org.partiql:.*"))
    include(dependency("joda-time:.*"))
  }

  // Relocate packages to avoid conflicts.
  listOf(
    "com.amazon.dynamodb.grammar",
    "com.amazon.ion",
    "com.amazonaws.services.dynamodbv2",
    "com.fasterxml.jackson",
    "ddb.partiql",
    "org.antlr",
    "org.eclipse.jetty",
    "org.joda.time",
    "org.partiql",
    "software.amazon.dynamodb.services",
  ).forEach { relocate(it, "app.cash.tempest.testing.dynamodb.local.shaded.${it}") }

  mergeServiceFiles()

  // Publish shadow JAR as the main JAR.
  archiveClassifier = ""
}

tasks.assemble {
  dependsOn(tasks.shadowJar)
}

val javaComponent = components["java"] as AdhocComponentWithVariants
listOf("apiElements", "runtimeElements")
  .map { configurations[it] }
  .forEach { unpublishable ->
    // Hide the un-shadowed variants in local consumption, by mangling their attributes
    unpublishable.attributes {
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named("DO_NOT_USE"))
    }

    // Hide the un-shadowed variants in publishing
    javaComponent.withVariantsFromConfiguration(unpublishable) { skip() }
  }

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
