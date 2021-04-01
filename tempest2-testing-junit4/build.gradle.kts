apply(plugin = "java-library")
apply(plugin = "kotlin")

dependencies {
  api(project(":tempest2-testing"))
  api(Dependencies.junit4Api)
  implementation(project(":tempest2-testing-internal"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinReflection)

  testImplementation(project(":samples:urlshortener2"))
  testImplementation(project(":tempest2-testing-jvm"))
  testImplementation(Dependencies.assertj)
}

tasks.withType<Test> {
  useJUnit()
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
