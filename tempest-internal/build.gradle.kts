apply(plugin = "java-library")
apply(plugin = "kotlin")
apply(plugin = "com.vanniktech.maven.publish")

dependencies {
  api(Dependencies.findbugsJsr305)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
