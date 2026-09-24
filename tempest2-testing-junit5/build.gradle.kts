import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":tempest2-testing"))
  api(libs.junitApi)
  implementation(project(":tempest2-testing-internal"))
  implementation(libs.kotlinStdLib)
  implementation(libs.guava)
  implementation(libs.kotlinReflection)

  testImplementation(project(":samples:urlshortener2"))
  testImplementation(project(":tempest2-testing-jvm"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testRuntimeOnly(libs.junitLauncher)
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
