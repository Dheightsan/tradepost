plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.dheightsan"
version = "1.0.0"

val paperVersion = "1.21.11-R0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io") {
            content { includeGroup("com.github.MilkBowl") }
        }
        maven("https://repo.extendedclip.com/releases/") {
            content { includeGroup("me.clip") }
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        isTransitive = false
    }
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("com.zaxxer:HikariCP:7.1.0") {
        // paper already has slf4j
        exclude(group = "org.slf4j")
    }

    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.54.1.0")
    // h2 in mysql mode for the mysql dialect test
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("me.clip:placeholderapi:2.11.6") {
        isTransitive = false
    }
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") {
        isTransitive = false
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "io.github.dheightsan.tradepost.lib.hikari")
        minimize()
    }

    jar {
        archiveClassifier.set("plain")
    }

    build {
        dependsOn(shadowJar)
    }
}
