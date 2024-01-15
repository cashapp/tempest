import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":tempest-testing"))
  api(libs.junit4Api)
  implementation(project(":tempest-testing-internal"))
  implementation(libs.kotlinStdLib)
  implementation(libs.guava)
  implementation(libs.kotlinReflection)

  testImplementation(project(":samples:urlshortener"))
  testImplementation(project(":tempest-testing-jvm"))
  testImplementation(libs.assertj)
}

tasks.withType<Test> {
  useJUnit()
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
