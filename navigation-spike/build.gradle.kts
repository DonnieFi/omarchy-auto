plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}
android {
    namespace = "com.omarchy.auto.navspike"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.omarchy.auto.navspike"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-routing-spike"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}
dependencies {
    implementation("com.stadiamaps.ferrostar:core:0.59.0")
    implementation("com.stadiamaps.ferrostar:ui-maplibre:0.59.0")
    implementation("com.stadiamaps.ferrostar:ui-compose:0.59.0")
    implementation("com.stadiamaps.ferrostar:car-app:0.59.0")
    implementation("com.stadiamaps.ferrostar:ui-maplibre-car-app:0.59.0")
    implementation("io.github.rallista:car-app-compose:0.0.2")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
