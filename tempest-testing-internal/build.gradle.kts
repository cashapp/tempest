plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":tempest-testing"))
  api(Dependencies.loggingApi)
  implementation(Dependencies.log4jCore)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
