apply(plugin = "kotlin")

dependencies {
  implementation(project(":tempest"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.clikt)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
