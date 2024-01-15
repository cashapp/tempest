plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest2"))
  implementation(libs.kotlinStdLib)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}
