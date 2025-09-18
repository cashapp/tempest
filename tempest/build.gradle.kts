import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm


plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.findbugsJsr305)
  implementation(project(":tempest-internal"))
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
  implementation(libs.okio)

  testImplementation(project(":samples:musiclibrary"))
  testImplementation(project(":samples:musiclibrary-testing"))
  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-jvm"))
  testImplementation(project(":tempest-testing-junit5"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testRuntimeOnly(libs.junitLauncher)
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
