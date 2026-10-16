plugins {
    id("com.android.application")
}

android {
    namespace = "com.mono.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mono.launcher"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    signingConfigs {
        // Same key on every build => a new APK installs as an UPDATE over the old one.
        // (These are the public, standard Android debug credentials - not a secret.)
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Wallpaper colour fallback
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Background loading
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
