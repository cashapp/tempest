plugins {
  kotlin("jvm")
  `java-library`
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
