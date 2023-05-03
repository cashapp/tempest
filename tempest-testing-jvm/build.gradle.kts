import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    kotlin("jvm")
    `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
    api(project(":tempest-testing"))
    implementation(project(":tempest-testing-internal"))
    implementation(Dependencies.awsDynamodbLocal)
    implementation(Dependencies.kotlinStdLib)

    if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
        implementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
    }

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.junitEngine)
}


configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
