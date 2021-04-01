apply(plugin = "java-library")
apply(plugin = "kotlin")

dependencies {
  api(project(":tempest-testing"))
  implementation(project(":tempest-testing-internal"))
  implementation(Dependencies.awsDynamodbLocal)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
