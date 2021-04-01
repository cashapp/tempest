apply(plugin = "kotlin")

dependencies {
  implementation(project(":tempest2"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.clikt)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
