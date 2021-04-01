import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    jcenter()
  }

  dependencies {
    classpath(Dependencies.junitGradlePlugin)
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.shadowJarPlugin)
    classpath(Dependencies.spotlessPlugin)
    classpath(Dependencies.wireGradlePlugin)
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "com.diffplug.gradle.spotless")
  apply(plugin = "org.jetbrains.dokka")

  repositories {
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }

  val compileKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    dependsOn("spotlessKotlinApply")
  }

  val compileTestKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
  }

  tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
  }

  configure<SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      ktlint(Dependencies.ktlintVersion).userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2"))
    }
  }

  repositories {
    mavenCentral()
    jcenter()
  }

  dependencies {
    add("api", enforcedPlatform(Dependencies.nettyBom))
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showStackTraces = true
    }
  }

  afterEvaluate {
    val dokka by tasks.getting(DokkaTask::class) {
      outputDirectory = "$rootDir/docs/1.x/"
      outputFormat = "gfm"
      externalDocumentationLink {
        url = java.net.URL("https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/")
      }
      externalDocumentationLink {
        url = java.net.URL("https://sdk.amazonaws.com/java/api/latest/")
      }
    }
  }

  // SLF4J uses the classpath to decide which logger to use! Banish the Log4J to prevent this:
  // org.apache.logging.slf4j.Log4jLogger cannot be cast to class ch.qos.logback.classic.Logger
  configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
  }

  // Workaround the Gradle bug resolving multiplatform dependencies.
  // https://github.com/square/okio/issues/647
  configurations.all {
    if (name.contains("kapt") || name.contains("wire") || name.contains("proto")) {
      attributes.attribute(Usage.USAGE_ATTRIBUTE, this@subprojects.objects.named(Usage::class, Usage.JAVA_RUNTIME))
    }
  }
}
