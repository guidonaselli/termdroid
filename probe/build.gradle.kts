plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termdroid.probe"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // El binario del probe tiene que quedar EXTRAIDO en disco, tambien en el APK
    // de test: comprimido dentro del APK no hay ruta que ejecutar.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // api: DeviceCapabilities expone ExecBackend en su superficie publica.
    api(project(":exec"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
