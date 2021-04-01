apply(plugin = "java-library")
apply(plugin = "kotlin")

dependencies {
  api(project(":tempest2-testing"))
  api(Dependencies.loggingApi)
  implementation(Dependencies.log4jCore)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
