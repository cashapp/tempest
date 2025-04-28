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
  // Ignore transtive dependencies and insyead manage explicitly.
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
  relocate("com.amazon.dynamodb.grammar", "shaded.com.amazon.dynamodb.grammar")
  relocate("com.amazon.ion", "shaded.com.amazon.ion")
  relocate("com.amazonaws.services.dynamodbv2.dataMembers", "shaded.com.amazonaws.services.dynamodbv2.dataMembers")
  relocate("com.amazonaws.services.dynamodbv2.datamodel", "shaded.com.amazonaws.services.dynamodbv2.datamodel")
  relocate("com.amazonaws.services.dynamodbv2.dbenv", "shaded.com.amazonaws.services.dynamodbv2.dbenv")
  relocate("com.amazonaws.services.dynamodbv2.exceptions", "shaded.com.amazonaws.services.dynamodbv2.exceptions")
  relocate("com.amazonaws.services.dynamodbv2.local", "shaded.com.amazonaws.services.dynamodbv2.local")
  relocate("com.fasterxml.jackson", "shaded.com.fasterxml.jackson")
  relocate("ddb.partiql", "shaded.ddb.partiql")
  relocate("kotlin", "shaded.kotlin")
  relocate("org.eclipse.jetty", "shaded.org.eclipse.jetty")
  relocate("org.partiql", "shaded.org.partiql")
  
  mergeServiceFiles()

  // Publish shadow JAR as the main JAR.
  archiveClassifier = ""
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
