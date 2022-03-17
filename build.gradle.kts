import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
    }

    dependencies {
        classpath(Dependencies.dokkaGradlePlugin)
        classpath(Dependencies.junitGradlePlugin)
        classpath(Dependencies.kotlinGradlePlugin)
        classpath(Dependencies.mavenPublishGradlePlugin)
        classpath(Dependencies.wireGradlePlugin)
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
        maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
    }

    // Only apply if the project has the kotlin plugin added:
    plugins.withType<KotlinPluginWrapper> {
        val compileKotlin by tasks.getting(KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "1.8"
                allWarningsAsErrors = false
            }
        }
        val compileTestKotlin by tasks.getting(KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "1.8"
                allWarningsAsErrors = false
            }
        }

        dependencies {
            add("api", enforcedPlatform(Dependencies.nettyBom))
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
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

    tasks.withType<DokkaTask>().configureEach {
        val dokkaTask = this
        dokkaSourceSets.configureEach {
            reportUndocumented.set(false)
            skipDeprecated.set(true)
            jdkVersion.set(8)
            if (dokkaTask.name == "dokkaGfm") {
                outputDirectory.set(project.file("$rootDir/docs/1.x"))
            }

// TODO: add external doc links...
//      externalDocumentationLink {
//        url = java.net.URL("https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/")
//      }
//      externalDocumentationLink {
//        url = java.net.URL("https://sdk.amazonaws.com/java/api/latest/")
//      }
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
