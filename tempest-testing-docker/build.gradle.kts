apply(plugin = "java-library")

dependencies {
  api(project(":tempest-testing"))
  implementation(project(":tempest-testing-internal"))
  implementation(Dependencies.kotlinStdLib)
  // The docker-java we use in tests depends on an old version of junixsocket that depends on
  // log4j. We force it up a minor version in packages that use it.
  implementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.3.3") {
    isForce = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.3.3") {
    isForce = true
  }
  implementation(Dependencies.docker)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-junit5"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
