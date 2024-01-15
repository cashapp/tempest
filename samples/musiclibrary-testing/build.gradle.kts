plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.findbugsJsr305)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}
