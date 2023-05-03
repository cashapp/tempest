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


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
