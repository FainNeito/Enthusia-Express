plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "io.enthusia"
version = "1.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://nexus.sirblobman.xyz/public/") { content { includeGroupByRegex("com\\.github\\.sirblobman.*") } }
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paperVersion").getOrElse("1.21")}-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("paperVersion").getOrElse("1.21")}-R0.1-SNAPSHOT")
    testImplementation("com.github.sirblobman.combatlogx:api:11.7-SNAPSHOT")
    testImplementation("com.github.sirblobman.api:core:2.9-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val pluginVersion = version.toString()

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Keep SQLite's JNI package and native-resource paths intact.
        mergeServiceFiles()
    }
    jar { archiveClassifier.set("plain") }
    test {
        useJUnitPlatform()
        dependsOn(shadowJar)
        systemProperty("pluginJar", shadowJar.get().archiveFile.get().asFile.absolutePath)
    }
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(21) }
    processResources { filesMatching("plugin.yml") { expand("version" to pluginVersion) } }
    withType<AbstractArchiveTask>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }
    build { dependsOn(shadowJar) }
}

// Explicit compatibility check; the distributable always defaults to the oldest supported API.
val supportedPaperVersions = listOf("1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")
val compatibilityTasks = supportedPaperVersions.map { paperVersion ->
    val api = configurations.create("paperApi" + paperVersion.replace(".", "_")) { isCanBeConsumed = false }
    dependencies.add(api.name, "io.papermc.paper:paper-api:$paperVersion-R0.1-SNAPSHOT")
    tasks.register<JavaCompile>("compilePaper" + paperVersion.replace(".", "_")) {
        source(sourceSets.main.get().allJava)
        classpath = api + configurations.runtimeClasspath.get()
        destinationDirectory.set(layout.buildDirectory.dir("compatibility/$paperVersion"))
        javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    }
}
tasks.register("verifyPaperCompatibility") {
    group = "verification"
    description = "Compile every source against each supported Paper 1.21 API release."
    dependsOn(compatibilityTasks)
}
