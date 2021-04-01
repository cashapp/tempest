apply(plugin = "java-library")
apply(plugin = "kotlin")

dependencies {
  api(project(":tempest2"))
  api(Dependencies.findbugsJsr305)
  api(Dependencies.guava)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
