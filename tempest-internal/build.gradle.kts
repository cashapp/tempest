import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.findbugsJsr305)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
