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
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("org.maplibre.gl:android-sdk:13.6.1")
}

tasks.withType<Test>().configureEach {
    // DesignTokensTest locates design/omarchy-themes.json from the repo root and
    // rewrites it when -Pomarchy.updateDesignTokens=true is present.
    systemProperty("omarchy.repo.root", rootProject.projectDir.absolutePath)
    if (project.hasProperty("omarchy.updateDesignTokens")) {
        systemProperty("omarchy.updateDesignTokens", project.property("omarchy.updateDesignTokens").toString())
    }
}
