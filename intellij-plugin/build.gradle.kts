plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "io.github.mm-asraf"
version = "1.0.3"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
}

intellij {
    // Compile against 2023.2 so APIs match older IDEs (232.*); supports IC/IDEA 2023.2+.
    version.set("2023.2.8")
    type.set("IC")
    updateSinceUntilBuild.set(false)
}

tasks {
    patchPluginXml {
        // 232 = IntelliJ 2023.2 (e.g. IC-232.x); 233 would exclude 2023.2 users.
        sinceBuild.set("232")
        untilBuild.set("252.*")
    }

    // Avoids headless IDE tasks that sometimes log ConcurrentModificationException from the
    // platform (not your plugin). Settings search won't index option labels; builds stay quiet.
    named("buildSearchableOptions") {
        enabled = false
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
