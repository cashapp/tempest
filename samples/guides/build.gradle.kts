import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(project(":tempest"))
  implementation(project(":samples:musiclibrary"))
  implementation(project(":samples:urlshortener"))
  implementation(libs.kotlinStdLib)
  implementation(libs.kotlinxCoroutines)
}

val compileKotlin by tasks.getting(KotlinCompile::class) {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
  }
}

val compileTestKotlin by tasks.getting(KotlinCompile::class) {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_11.toString()
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_11.toString()
  targetCompatibility = JavaVersion.VERSION_11.toString()
}
