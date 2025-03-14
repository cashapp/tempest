import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.moshiCore)
  api(libs.dockerCore)
  implementation(libs.moshiKotlin)
  implementation(libs.okio)

  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
  testImplementation(libs.assertj)
  testImplementation(libs.okioFakefilesystem)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
