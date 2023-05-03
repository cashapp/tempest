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
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
  testImplementation(project(":samples:urlshortener2"))
  testImplementation(project(":tempest2-testing-junit5"))
}

publish.KotlinJvm
configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
