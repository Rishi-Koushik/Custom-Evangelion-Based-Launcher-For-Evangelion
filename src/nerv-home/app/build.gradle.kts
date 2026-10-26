plugins {
    id("com.android.application")
}

android {
    namespace = "dev.nervos.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.nervos.home"
        minSdk = 33 // RuntimeShader for the unlock ripple
        targetSdk = 35
        versionCode = 16
        versionName = "1.6"
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
