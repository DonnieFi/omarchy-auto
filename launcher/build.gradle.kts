plugins {
    id("com.android.application")
}

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core:1.19.1")
}
