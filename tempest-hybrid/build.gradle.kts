import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":tempest2"))

  // AWS SDK for DynamoDB and S3
  api(libs.awsDynamodb)
  api("com.amazonaws:aws-java-sdk-s3:1.12.791")

  // Jackson for JSON serialization
  api(libs.jacksonDatabind)
  api("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")

  // Logging
  api(libs.slf4jApi)

  // Kotlin
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)

  // Testing
  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(project(":tempest2-testing-jvm"))
  testImplementation(project(":tempest2-testing-junit5"))
  testRuntimeOnly(libs.junitLauncher)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}