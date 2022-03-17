plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":tempest2-testing"))
  implementation(project(":tempest2-testing-internal"))
  implementation(Dependencies.awsDynamodbLocal)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
