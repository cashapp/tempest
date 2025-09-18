plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest2"))
  implementation(libs.kotlinStdLib)
  implementation(libs.clikt)
}
