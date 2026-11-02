plugins {
    id("com.android.application")
}

// Private/local wallpaper opt-in. Clean and public builds exclude local-assets
// unless the private release is explicitly built with
// -Pomarchy.includeLocalAssets=true.
val includeLocalAssets = (findProperty("omarchy.includeLocalAssets") as String?)
    ?.toBooleanStrictOrNull() ?: false

android {
    namespace = "com.omarchy.auto.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.omarchy.auto.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    if (includeLocalAssets) {
        sourceSets["debug"].assets.srcDir(rootProject.file("local-assets"))
        sourceSets["release"].assets.srcDir(rootProject.file("local-assets"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core:1.19.1")
}
