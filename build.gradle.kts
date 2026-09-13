import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "io.enthusia"
version = "1.2.1"

// Keep the released JAR on the oldest API. Newer targets are verification builds.
val paperVersion = providers.gradleProperty("paperVersion").getOrElse("1.21")
val modernPaperApis = mapOf(
    "26.2" to "26.2.build.123-stable",
    "26.3-pre-2" to "26.3-pre-2.build.0-alpha"
)
val paperApiVersion = modernPaperApis[paperVersion] ?: run {
    require(paperVersion.startsWith("1.21")) {
        "Unverified Paper target: $paperVersion. Use 26.3-pre-2 for experimental 26.3 verification."
    }
    "$paperVersion-R0.1-SNAPSHOT"
}
val targetJava = if (paperVersion in modernPaperApis) 25 else 21
val baselineJar = providers.gradleProperty("baselineJar")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
}
kotlin { jvmToolchain(targetJava) }

repositories {
    mavenCentral()
    maven("https://jitpack.io") { content { includeGroup("com.github.MilkBowl") } }
    maven("https://nexus.sirblobman.xyz/public/") { content { includeGroupByRegex("com\\.github\\.sirblobman.*") } }
    maven("https://repo.papermc.io/repository/maven-public/")
}

val combatApi = "com.github.sirblobman.combatlogx:api:11.7-SNAPSHOT"
val combatCore = "com.github.sirblobman.api:core:2.9-SNAPSHOT"
val vaultApi = "com.github.MilkBowl:VaultAPI:1.7.1"

dependencies {
    compileOnly(vaultApi) { isTransitive = false }
    testImplementation(vaultApi) { isTransitive = false }
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly(combatApi)
    compileOnly(combatCore)
    testImplementation(combatApi)
    testImplementation(combatCore)
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testImplementation("org.ow2.asm:asm:9.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val pluginVersion = version.toString()

tasks {
    shadowJar {
        archiveClassifier.set(if (targetJava == 21) "" else "verify-$paperVersion")
        // Keep SQLite's JNI package and native-resource paths intact.
        mergeServiceFiles()
    }
    jar { archiveClassifier.set("plain") }
    test {
        useJUnitPlatform()
        dependsOn(shadowJar)
        if (baselineJar.isPresent) {
            val artifact = file(baselineJar.get())
            inputs.file(artifact)
            // Exercise the actual distributable against the newer server API at runtime.
            classpath = files(artifact) + (classpath - sourceSets.main.get().output)
            systemProperty("pluginJar", artifact.absolutePath)
        } else {
            systemProperty("pluginJar", shadowJar.get().archiveFile.get().asFile.absolutePath)
        }
    }
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(targetJava) }
    withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJava.toString())) }
    processResources {
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("plugin.yml") { expand("version" to pluginVersion) }
    }
    withType<AbstractArchiveTask>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }
    build { dependsOn(shadowJar) }
}

// Explicit compatibility check; the distributable always defaults to the oldest supported API.
val supportedPaperVersions = listOf("1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")
val compatibilityTasks = supportedPaperVersions.map { paperVersion ->
    val name = "paper" + paperVersion.replace(".", "_")
    val sourceSet = sourceSets.create(name)
    kotlin.sourceSets.getByName(name).kotlin.srcDir("src/main/kotlin")
    dependencies.add(sourceSet.compileOnlyConfigurationName, "io.papermc.paper:paper-api:$paperVersion-R0.1-SNAPSHOT")
    dependencies.add(sourceSet.compileOnlyConfigurationName, combatApi)
    dependencies.add(sourceSet.compileOnlyConfigurationName, combatCore)
    dependencies.add(sourceSet.compileOnlyConfigurationName, vaultApi) { isTransitive = false }
    configurations.getByName(sourceSet.implementationConfigurationName).extendsFrom(configurations.implementation.get())
    tasks.named(sourceSet.getCompileTaskName("kotlin"))
}
tasks.register("verifyPaperCompatibility") {
    group = "verification"
    description = "Compile every source against each supported Paper 1.21 API release."
    dependsOn(compatibilityTasks)
}
