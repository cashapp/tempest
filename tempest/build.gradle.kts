apply(plugin = "java-library")
apply(plugin = "kotlin")
apply(plugin = "com.vanniktech.maven.publish")

dependencies {
  api(Dependencies.awsDynamodb)
  api(Dependencies.findbugsJsr305)
  implementation(project(":tempest-internal"))
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.okio)

  testImplementation(project(":samples:musiclibrary"))
  testImplementation(project(":samples:musiclibrary-testing"))
  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-jvm"))
  testImplementation(project(":tempest-testing-junit5"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
