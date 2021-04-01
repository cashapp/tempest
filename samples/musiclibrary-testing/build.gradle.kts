apply(plugin = "java-library")
apply(plugin = "kotlin")

dependencies {
  api(Dependencies.findbugsJsr305)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}
