import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import java.net.URL

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

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
    group = project.property("GROUP") as String
    version = project.property("VERSION_NAME") as String
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
        val compileKotlin by tasks.getting(KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "1.8"
                allWarningsAsErrors = false
                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }
        val compileTestKotlin by tasks.getting(KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "1.8"
                allWarningsAsErrors = false
            }
        }

        dependencies {
            // add("api", project(":tempest-bom"))
            add("api", platform(Dependencies.nettyBom))
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
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
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

        externalDocumentationLink {
            url.set(URL("https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/"))
        }

            externalDocumentationLink {
                url.set(URL("https://sdk.amazonaws.com/java/api/latest/"))
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

allprojects {
    plugins.withId("com.vanniktech.maven.publish.base") {
        val publishingExtension = extensions.getByType(PublishingExtension::class.java)
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

