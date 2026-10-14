plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.jvc.progame"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.jvc.progame"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "1.0-r15"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.postgresql:postgresql:42.2.27")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
