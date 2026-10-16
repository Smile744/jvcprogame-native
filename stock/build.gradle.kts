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
        versionCode = 9
        versionName = "1.0-s9"
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
    // 42.2.9 es la última que no llama a java.lang.management (no existe en Android): desde 42.2.10 revienta al conectar
    implementation("org.postgresql:postgresql:42.2.9")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
