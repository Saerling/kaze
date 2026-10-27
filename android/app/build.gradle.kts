plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// En GitHub Actions cada compilación sube el número de versión (así se instala como actualización).
val ciVersionCode = System.getenv("KAZE_VERSION_CODE")?.toIntOrNull()
// Clave fija de publicación: con la misma firma, Android acepta cada APK nuevo como actualización.
val keystorePassword: String? = System.getenv("KAZE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.kaze.browser"
    // GeckoView 158 y Compose 2026.09 piden compilar contra Android 17 QPR2 (API 37.2)
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.kaze.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 2
        versionName = "0.3.${ciVersionCode ?: 0}"
        // Repositorio donde la app busca actualizaciones (GitHub Releases con etiqueta android-<versionCode>)
        buildConfigField("String", "UPDATE_REPO", "\"Saerling/kaze\"")
    }

    signingConfigs {
        if (keystorePassword != null) {
            create("release") {
                storeFile = rootProject.file("keystore/kaze-release.p12")
                storeType = "PKCS12"
                storePassword = keystorePassword
                keyAlias = "kaze"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Con la contraseña (secreto de GitHub) firma con la clave fija; si no, con la de depuración (solo pruebas locales).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // GeckoView trae librerías nativas por arquitectura: generar un APK por ABI
    // reduce mucho el tamaño (cada APK lleva solo el motor de su procesador).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Librerías nativas comprimidas dentro del APK (como Firefox para Android): descarga
        // mucho más pequeña (~70 MB en vez de ~185 MB) y Android las extrae al instalar.
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(libs.geckoview)
    implementation("androidx.security:security-crypto:1.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
