plugins {
    id("com.android.library")
}

android {
    namespace = "com.omarchy.auto.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("org.maplibre.gl:android-sdk:13.6.1")
}
