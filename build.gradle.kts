import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath(libs.dokkaGradlePlugin)
    classpath(libs.jacksonDatabind)
    classpath(libs.junitGradlePlugin)
    classpath(libs.kotlinGradlePlugin)
    classpath(libs.mavenPublishGradlePlugin)
    classpath(libs.shadowGradlePlugin)
    classpath(libs.wireGradlePlugin)
  }
}

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
  group = "app.cash.tempest"
  version = project.findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.DEFAULT, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("Typesafe DynamoDB in Kotlin")
        name.set(project.name)
        url.set("https://github.com/cashapp/tempest/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/cashapp/tempest/")
          connection.set("scm:git:git://github.com/cashapp/tempest.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/tempest.git")
        }
        developers {
          developer {
            id.set("square")
            name.set("Square, Inc.")
          }
        }
      }
    }
  }
}

subprojects {
  if (project.name == "tempest-bom") return@subprojects

  apply(plugin = "org.jetbrains.dokka")

  repositories {
    mavenCentral()
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }

  // Only apply if the project has the kotlin plugin added:
  plugins.withType<KotlinPluginWrapper> {
    tasks.withType<KotlinCompile> {
      kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
        freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
      }
      // dependsOn("spotlessKotlinApply")
    }

    tasks.withType<JavaCompile> {
      sourceCompatibility = JavaVersion.VERSION_11.toString()
      targetCompatibility = JavaVersion.VERSION_11.toString()
    }

    dependencies {
      // add("api", project(":tempest-bom"))
      add("api", platform(libs.nettyBom))
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showStackTraces = true
    }
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  }

  tasks.withType<DokkaTask>().configureEach {
    val dokkaTask = this

    if (dokkaTask.name == "dokkaGfm") {
      outputDirectory.set(project.file("$rootDir/docs/1.x/${project.name}"))
    }

    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(11)

      externalDocumentationLink {
        url.set(URL("https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/"))
        packageListUrl.set(
          URL("https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/package-list")
        )
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
      attributes.attribute(
        Usage.USAGE_ATTRIBUTE,
        this@subprojects.objects.named(Usage::class, Usage.JAVA_RUNTIME)
      )
    }
  }
}
