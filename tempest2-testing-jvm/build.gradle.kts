import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":tempest2-testing"))
  implementation(project(":tempest2-testing-internal"))
  implementation(libs.awsDynamodbLocal)
  implementation(libs.kotlinStdLib)

  // Needed for com.amazonaws:DynamoDBLocal to work on macOS Aarch64 machines
  implementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
