plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":tempest-testing"))
  api(Dependencies.junitApi)
  implementation(project(":tempest-testing-internal"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinReflection)

  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-jvm"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
}
