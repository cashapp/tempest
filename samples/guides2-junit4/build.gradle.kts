import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply(plugin = "kotlin")

dependencies {
  implementation(project(":tempest2"))
  implementation(project(":samples:musiclibrary2"))
  implementation(project(":samples:urlshortener2"))
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.kotlinxCoroutines)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junit4Api)
  implementation(project(":tempest2-testing-docker"))
  implementation(project(":tempest2-testing-jvm"))
  implementation(project(":tempest2-testing-junit4"))
}

tasks.withType<Test> {
  useJUnit()
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
