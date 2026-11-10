import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing: path comes from the Gradle property `omarchy.keystoreProperties`
// (e.g. in ~/.gradle/gradle.properties) or the env var OMARCHY_KEYSTORE_PROPERTIES.
// The file and keystore live outside the repo. If absent, release stays unsigned.
val keystorePropsFile = (findProperty("omarchy.keystoreProperties") as String?
    ?: System.getenv("OMARCHY_KEYSTORE_PROPERTIES"))?.let { file(it) }
val keystoreProps = Properties().apply {
    if (keystorePropsFile?.isFile == true) keystorePropsFile.inputStream().use { load(it) }
}

// Private/local wallpaper opt-in. Clean and public builds exclude local-assets
// unless the private release is explicitly built with
// -Pomarchy.includeLocalAssets=true.
// Crash upload token, from -Pomarchy.crashToken, ~/.gradle/gradle.properties
// or local.properties. Not committed. It ships inside the APK, so it is abuse
// friction for the endpoint, not a secret.
val crashToken = (findProperty("omarchy.crashToken") as String?)
    ?: Properties().apply {
        rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
    }.getProperty("omarchy.crashToken")
    ?: ""

val includeLocalAssets = (findProperty("omarchy.includeLocalAssets") as String?)
    ?.toBooleanStrictOrNull() ?: false

android {
    namespace = "com.omarchy.auto"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.omarchy.auto"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.1.18"
        buildConfigField("String", "CRASH_TOKEN", "\"${crashToken.replace("\"", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    if (keystoreProps.getProperty("storeFile") != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    if (includeLocalAssets) {
        sourceSets["debug"].assets.srcDir(rootProject.file("local-assets"))
        sourceSets["release"].assets.srcDir(rootProject.file("local-assets"))
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.core:core:1.19.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.car.app:app-testing:1.7.0")
    testImplementation("androidx.test:core:1.6.1")
}
