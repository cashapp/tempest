plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":tempest-testing"))
  implementation(project(":tempest-testing-internal"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-junit5"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")