import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":tempest"))
  api(libs.awsDynamodb)
  api(libs.findbugsJsr305)
  
  implementation(project(":tempest-internal"))
  implementation(libs.kotlinStdLib)
  implementation(libs.kotlinxCoroutines)
  implementation("com.amazonaws:aws-java-sdk-s3:1.12.782")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.3")
  implementation("com.fasterxml.jackson.core:jackson-core:2.14.3")

  testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
  testImplementation("org.assertj:assertj-core:3.21.0")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
