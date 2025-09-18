import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest2"))
  implementation(project(":samples:musiclibrary2"))
  implementation(project(":samples:urlshortener2"))
  implementation(libs.kotlinStdLib)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
  implementation(project(":tempest2-testing-docker"))
  implementation(project(":tempest2-testing-jvm"))
  implementation(project(":tempest2-testing-junit5"))
}

val compileKotlin by tasks.getting(KotlinCompile::class) {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

val compileTestKotlin by tasks.getting(KotlinCompile::class) {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_11.toString()
  targetCompatibility = JavaVersion.VERSION_11.toString()
}
