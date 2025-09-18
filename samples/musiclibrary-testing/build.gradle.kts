plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.findbugsJsr305)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
}
