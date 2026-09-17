pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.2"
}

extra["mod.id"] = "bloodmod"

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) = loaders.forEach { loader ->
            val buildFile = when {
                loader != "fabric" -> "build.$loader.gradle"
                version.startsWith("1.") -> "build.fabric-legacy.gradle" // obfuscated 1.21.x
                else -> "build.fabric.gradle"                            // unobfuscated 26.x
            }
            version("$version-$loader", version).buildscript = buildFile
        }

        match("1.21.1", "fabric", "neoforge")
        match("1.21.11", "fabric", "neoforge")
        match("26.1", "fabric", "neoforge")
        match("26.1.1", "fabric", "neoforge")
        match("26.1.2", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")

        vcsVersion = "26.1.2-fabric"
    }
}

rootProject.name = "SimpleBlood"
