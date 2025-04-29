import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.gradleup.shadow")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  // Ignore transtive dependencies and instead manage explicitly.
  implementation(libs.awsDynamodbLocal) {
    isTransitive = false
  }

  // Implementation dependencies will be shaded in the JAR.
  implementation(libs.bundles.jackson)
  implementation(libs.bundles.jetty)
  implementation(libs.kotlinStdLib)

  // Shadow dependencies will not be shaded.
  shadow(libs.antlr4Runtime)
  shadow(libs.aws2Dynamodb)
  shadow(libs.aws2DynamodbEnhanced)
  shadow(libs.aws2Pinpoint)
  shadow(libs.awsDynamodb)
  shadow(libs.commonsCli)
  shadow(libs.commonsLang3)
  shadow(libs.guava)
  shadow(libs.slf4jApi)
  shadow(libs.bundles.sqlite4java)
}

tasks.named<Jar>("jar") {
  archiveClassifier.set("unshaded")
}

tasks.shadowJar {
  // Dependencies to be shaded must be explicitly included as dependencies.
  dependencies {
    include(dependency("com.amazonaws:DynamoDBLocal"))
    include(dependency("com.fasterxml.jackson.core:.*"))
    include(dependency("com.fasterxml.jackson.dataformat:.*"))
    include(dependency("com.fasterxml.jackson.datatype:.*"))
    include(dependency("com.fasterxml.jackson.module:.*"))
    include(dependency("org.eclipse.jetty:.*"))
  }

  // Relocate packages to avoid conflicts.
  listOf(
    "com.amazon.dynamodb.grammar",
    "com.amazon.ion",
    "com.amazonaws.services.dynamodbv2.dataMembers",
    "com.amazonaws.services.dynamodbv2.datamodel",
    "com.amazonaws.services.dynamodbv2.dbenv",
    "com.amazonaws.services.dynamodbv2.exceptions",
    "com.amazonaws.services.dynamodbv2.local",
    "com.fasterxml.jackson",
    "ddb.partiql",
    "kotlin",
    "org.eclipse.jetty",
    "org.partiql",
  ).forEach { relocate(it, "app.cash.tempest.testing.dynamodb.local.shaded.${it}") }

  mergeServiceFiles()

  // Publish shadow JAR as the main JAR.
  archiveClassifier = ""
}

// Override all published JARs to point at the shadow jar
listOf(configurations["apiElements"], configurations["runtimeElements"]).forEach {
  it.outgoing {
    artifacts.clear()
    artifact(tasks.named("shadowJar"))
  }
}

// Post-process the gradle module metadata JSON to use the shadow JAR's dependencies as the runtime
// dependencies in gradle builds.
tasks.withType<GenerateModuleMetadata>().configureEach {
  doLast {
    try {
      outputFile.get().asFile.takeIf { it.exists() }?.let { moduleFile ->
        val mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false)
            .build()
        val moduleJson = mapper.readTree(moduleFile) as ObjectNode
        val variants = moduleJson.get("variants") as ArrayNode

        val shadowVariant = variants
          .elements().asSequence()
          .map { it as ObjectNode }
          .find { it.get("name").asText() == "shadowRuntimeElements" }
          ?: throw NoSuchElementException("could not find the `shadowRuntimeElements` variant!")

        val runtimeVariant = variants
          .elements().asSequence()
          .map { it as ObjectNode }
          .find { it.get("name").asText() == "runtimeElements" }
          ?: throw NoSuchElementException("could not find the `runtimeElements` variant!")

        runtimeVariant.replace("dependencies", shadowVariant.get("dependencies"))

        moduleFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(moduleJson))
      }
    } catch (e: Exception) {
      throw GradleException("could not post-process the module metadata!", e)
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )

  pom {
    withXml {
      val root = asNode()

      // First collect all dependencies nodes.
      val dependenciesNodes = root.children()
        .filterIsInstance<groovy.util.Node>()
        .filter { it.name().toString().contains("dependencies") }
        .toList()

      // Then remove them safely.
      dependenciesNodes.forEach { node ->
        root.remove(node)
      }

      // Add a new dependencies node with shadow configuration.
      val dependenciesNode = root.appendNode("dependencies")

      // Add all shadow dependencies to the POM.
      project.configurations.named("shadow").get().allDependencies.forEach { dep ->
        val dependencyNode = dependenciesNode.appendNode("dependency")
        dependencyNode.appendNode("groupId", dep.group)
        dependencyNode.appendNode("artifactId", dep.name)
        dependencyNode.appendNode("version", dep.version)
        dependencyNode.appendNode("scope", "compile")
      }
    }
  }
}
