import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

apply(plugin = "kotlin")

dependencies {
  implementation(project(":tempest"))

  testImplementation(project(":tempest-testing-jvm"))
  testImplementation(project(":tempest-testing-junit5"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testRuntimeOnly(libs.junitLauncher)
}

val compileKotlin by tasks.getting(KotlinCompile::class) {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_14)
  }
}

val compileTestKotlin by tasks.getting(KotlinCompile::class) {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_14)
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_14.toString()
  targetCompatibility = JavaVersion.VERSION_14.toString()
}

tasks.withType<JavaCompile> {
  options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test> {
  jvmArgs("--enable-preview")
}

tasks.withType<JavaExec> {
  jvmArgs("--enable-preview")
}
