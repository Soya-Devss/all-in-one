plugins {
    java
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.15"
}

group = "com.github.Soya-Devss"

// Resolve version dynamically:
// 1. -Pversion argument (used by JitPack when building a tag/commit)
// 2. VERSION environment variable
// 3. Git tag if available
// 4. Default fallback version
fun resolveVersion(): String {
    val versionProp = findProperty("version")?.toString()?.takeIf { it != "unspecified" && it.isNotBlank() }
    if (versionProp != null) return versionProp

    val envVersion = System.getenv("VERSION")?.takeIf { it.isNotBlank() }
    if (envVersion != null) return envVersion

    try {
        val process = ProcessBuilder("git", "describe", "--tags", "--exact-match")
            .redirectErrorStream(true)
            .start()
        val tag = process.inputStream.bufferedReader().readLine()?.trim()
        if (process.waitFor() == 0 && !tag.isNullOrBlank()) {
            return tag
        }
    } catch (_: Exception) {
        // Git not available or not on a tag
    }

    return "1.1.0"
}

version = resolveVersion()

lavalinkPlugin {
    name = "all-in-one-plugin"
    path = "com.github.allinone"
    apiVersion = "4.0.0"
    serverVersion = "4.0.8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.topi.wtf/releases") }
    maven { url = uri("https://maven.lavalink.dev/releases") }
    maven { url = uri("https://maven.lavalink.dev/snapshots") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.json:json:20240303")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.github.topi314.lavasearch:lavasearch-plugin-api:1.0.0")
    compileOnly("com.github.topi314.lavasearch:lavasearch:1.0.0")
    // Needed for AudioPluginInfoModifier (playlist pluginInfo support). Resolves
    // above the plugin-api version pulled in by the Lavalink Gradle plugin.
    compileOnly("dev.arbjerg.lavalink:plugin-api:4.0.8")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
