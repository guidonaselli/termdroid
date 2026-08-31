plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.termdroid"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.termdroid"
        minSdk = 26
        targetSdk = 28
        versionCode = 8
        versionName = "0.1.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Nivel 1 del modelo de ejecucion: los binarios viajan como lib*.so y deben
    // quedar extraidos en disco dentro de nativeLibraryDir. Comprimidos no hay
    // ruta que ejecutar. Ver 10_TECH/EXEC_MODEL.md.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Un APK por ABI: el bootstrap viaja en jniLibs y un universal multiplica el
    // tamano sin beneficio. Ver 10_TECH/COMPATIBILITY.md.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = project.rootProject.file("app/release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "termdroid_release_key"
                keyAlias = System.getenv("KEY_ALIAS") ?: "termdroid"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "termdroid_release_key"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += listOf("ExpiredTargetSdkVersion")
    }

    buildFeatures {
        compose = true
    }
}


dependencies {
    implementation(project(":core"))
    implementation(project(":agent"))
    implementation(project(":probe"))
    implementation(project(":exec"))
    implementation(project(":terminal"))
    implementation(project(":rootfs"))
    implementation(project(":tools-unix"))
    implementation(project(":tools-android"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
