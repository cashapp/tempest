import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.aws2Dynamodb)
  api(libs.aws2DynamodbEnhanced)
  api(libs.findbugsJsr305)
  api(libs.kotlinxCoroutines)
  implementation(libs.kotlinxCoroutinesJdk8)
  implementation(libs.kotlinxCoroutinesReactive)
  implementation(project(":tempest-internal"))
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)

  testImplementation(project(":samples:musiclibrary2"))
  testImplementation(project(":samples:musiclibrary-testing"))
  testImplementation(project(":samples:urlshortener2"))
  testImplementation(project(":tempest2-testing-jvm"))
  testImplementation(project(":tempest2-testing-junit5"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}

