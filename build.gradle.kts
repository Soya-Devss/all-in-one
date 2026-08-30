plugins {
    java
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.15"
}

group = "com.github.allinone"
version = "1.0.0"

lavalinkPlugin {
    name = "all-in-one-plugin"
    apiVersion = "4.0.0"
    serverVersion = "4.0.8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}
