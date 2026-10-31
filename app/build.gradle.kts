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
        versionCode = 9
        versionName = "2.2"
    }

    // Firma fija (firma.jks + secreto FIRMA_CLAVE en GitHub): todas las versiones
    // quedan firmadas igual y se pueden instalar encima sin desinstalar.
    // Sin la clave, se firma con la llave de prueba.
    val firma = rootProject.file("firma.jks")
    val clave = System.getenv("FIRMA_CLAVE")
    val conFirma = firma.exists() && !clave.isNullOrBlank()
    signingConfigs {
        create("propia") {
            storeFile = firma
            storePassword = clave ?: ""
            keyAlias = "inicio"
            keyPassword = clave ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (conFirma) signingConfigs.getByName("propia")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
