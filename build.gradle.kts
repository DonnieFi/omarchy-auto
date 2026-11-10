plugins {
    id("com.android.application") version "9.5.0" apply false
    id("com.android.library") version "9.5.1" apply false
}

val forbiddenLocalAssets = providers.provider {
    val directory = rootProject.file("local-assets")
    if (!directory.isDirectory) {
        emptyList<String>()
    } else {
        directory.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(directory).invariantSeparatorsPath }
            .toList()
    }
}

tasks.register("verifyPublicReleaseAssets") {
    group = "verification"
    dependsOn(":auto:bundleRelease", ":launcher:assembleRelease")
    description = "Fails if a public release archive contains private local-assets overrides."

    doLast {
        val localAssets = forbiddenLocalAssets.get()
        if (localAssets.isEmpty()) {
            logger.lifecycle("No local-assets overrides present; public asset guard passes.")
            return@doLast
        }
        val archives = listOf(
            rootProject.file("auto/build/outputs/bundle/release/auto-release.aab"),
            rootProject.file("auto/build/outputs/apk/release/auto-release.apk"),
            rootProject.file("launcher/build/outputs/apk/release/launcher-release-unsigned.apk"),
            rootProject.file("launcher/build/outputs/apk/release/launcher-release.apk"),
        ).filter { it.isFile }
        if (archives.isEmpty()) {
            throw GradleException("No release archives found; build release artifacts before running this guard.")
        }
        var found = false
        for (archive in archives) {
            java.util.zip.ZipFile(archive).use { zip ->
                for (name in localAssets) {
                    val entry = if (name.startsWith("assets/")) name else "assets/$name"
                    if (zip.getEntry(entry) != null) {
                        logger.error("Private local asset '$name' found in ${archive.name}")
                        found = true
                    }
                }
            }
        }
        if (found) {
            throw GradleException(
                "Public release contains local-assets overrides. Rebuild without " +
                    "-Pomarchy.includeLocalAssets=true, or keep the artifact private.",
            )
        }
        logger.lifecycle("Public asset guard passes: no local-assets entries in release archives.")
    }
}
