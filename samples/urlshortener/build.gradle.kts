plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.clikt)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
