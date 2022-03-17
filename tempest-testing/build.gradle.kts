plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":tempest"))
  api(Dependencies.findbugsJsr305)
  api(Dependencies.guava)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
