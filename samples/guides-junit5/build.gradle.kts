import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply(plugin = "kotlin")

dependencies {
  implementation(project(":tempest"))
  implementation(project(":samples:musiclibrary"))
  implementation(project(":samples:urlshortener"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.kotlinxCoroutines)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  implementation(project(":tempest-testing-docker"))
  implementation(project(":tempest-testing-jvm"))
  implementation(project(":tempest-testing-junit5"))
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
