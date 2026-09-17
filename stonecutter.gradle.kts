plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.16.1" apply false        // unobfuscated MC (26.x)
    id("net.fabricmc.fabric-loom-remap") version "1.16.1" apply false  // obfuscated MC (1.21.x)
    id("net.neoforged.moddev") version "2.0.147" apply false
}

stonecutter active "26.1.2-fabric" /* [SC] DO NOT edit */

stonecutter.parameters {
    // Active loader constant, for `//? if fabric {` style preprocessor blocks.
    val loader = current.project.substringAfterLast('-')
    constants.match(loader, "fabric", "neoforge")

    // Token swaps consumed by `/*$ token */` markers in the source.
    swaps["mod_version"] = "\"${property("mod_version")}\";"
    swaps["minecraft"] = "\"${current.version}\";"
}
