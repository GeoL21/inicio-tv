plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.geol.inicio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geol.inicio"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Firma fija: si existe firma.jks (o los secretos de GitHub), todas las
    // versiones quedan firmadas igual y se pueden actualizar sin desinstalar.
    val firma = rootProject.file("firma.jks")
    signingConfigs {
        create("propia") {
            storeFile = firma
            storePassword = System.getenv("FIRMA_CLAVE") ?: "inicio-tv"
            keyAlias = "inicio"
            keyPassword = System.getenv("FIRMA_CLAVE") ?: "inicio-tv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (firma.exists()) signingConfigs.getByName("propia")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
