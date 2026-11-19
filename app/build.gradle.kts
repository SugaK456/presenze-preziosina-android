plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val numeroBuild = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "it.preziosina.presenze"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.preziosina.presenze"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroBuild
        versionName = "1.0.$numeroBuild"
    }

    // Chiave di firma fissa (serve perché gli aggiornamenti si installino sopra la versione precedente).
    // NON è nel repository: GitHub Actions la ricrea dai secret KEYSTORE_BASE64 e KEYSTORE_PASSWORD.
    val keystore = file("presenze.keystore")
    val passwordKeystore = System.getenv("KEYSTORE_PASSWORD")
    val firmaRelease = keystore.exists() && !passwordKeystore.isNullOrEmpty()
    signingConfigs {
        if (firmaRelease) {
            create("release") {
                storeFile = keystore
                storePassword = passwordKeystore
                keyAlias = "presenze"
                keyPassword = passwordKeystore
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (firmaRelease) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
