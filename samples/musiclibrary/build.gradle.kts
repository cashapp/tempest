plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest"))
  implementation(libs.kotlinStdLib)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}
