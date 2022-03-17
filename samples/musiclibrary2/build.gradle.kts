plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest2"))
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
