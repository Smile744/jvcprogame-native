plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.jvc.stock"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.jvc.stock"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-s1"
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
}

dependencies {
    implementation("org.postgresql:postgresql:42.2.27")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
