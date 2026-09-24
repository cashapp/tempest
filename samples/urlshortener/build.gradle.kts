plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest"))
  implementation(libs.kotlinStdLib)
  implementation(libs.clikt)
}
