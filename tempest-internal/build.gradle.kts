import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.findbugsJsr305)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
}

// // apply(from = "$rootDir/gradle-mvn-publish.gradle")

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
