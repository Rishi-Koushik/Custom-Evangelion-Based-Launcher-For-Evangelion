plugins {
    id("com.android.application")
}

android {
    namespace = "dev.nervos.icons"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nervos.icons"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug key for now so it installs with adb; swap in your own keystore before sharing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
