apply(plugin = "java-library")
apply(plugin = "kotlin")

dependencies {
  api(project(":tempest-testing"))
  api(Dependencies.junit4Api)
  implementation(project(":tempest-testing-internal"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinReflection)

  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-jvm"))
  testImplementation(Dependencies.assertj)
}

tasks.withType<Test> {
  useJUnit()
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
