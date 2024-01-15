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
  implementation(libs.kotlinStdLib)
  implementation(libs.dockerCore)
  implementation(libs.dockerTransport)

  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(project(":samples:urlshortener2"))
  testImplementation(project(":tempest2-testing-junit5"))
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
