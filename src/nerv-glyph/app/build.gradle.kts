plugins {
    id("com.android.application")
}

android {
    namespace = "dev.nervos.glyph"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nervos.glyph"
        minSdk = 33 // Glyph Matrix SDK requirement
        targetSdk = 35
        versionCode = 7
        versionName = "0.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug key for now so it installs with adb; swap in your own keystore before sharing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Nothing's Glyph Matrix SDK (licensed for use in our apps only; not redistributed)
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
}
