import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":tempest-testing"))
  api(project(":tempest-docker"))
  implementation(project(":tempest-testing-internal"))
  implementation(libs.kotlinStdLib)
  implementation(libs.dockerCore)
  implementation(libs.dockerTransport)

  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-junit5"))
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
