apply(plugin = "java-library")
apply(plugin = "kotlin")
apply(plugin = "com.vanniktech.maven.publish")

dependencies {
  api(Dependencies.aws2Dynamodb)
  api(Dependencies.aws2DynamodbEnhanced)
  api(Dependencies.findbugsJsr305)
  api(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.kotlinxCoroutinesJdk8)
  implementation(Dependencies.kotlinxCoroutinesReactive)
  implementation(project(":tempest-internal"))
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(project(":samples:musiclibrary2"))
  testImplementation(project(":samples:musiclibrary-testing"))
  testImplementation(project(":samples:urlshortener2"))
  testImplementation(project(":tempest2-testing-jvm"))
  testImplementation(project(":tempest2-testing-junit5"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
