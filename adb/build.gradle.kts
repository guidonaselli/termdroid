plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termdroid.adb"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.all {
            it.testLogging { showStandardStreams = true }
            it.systemProperty("java.net.preferIPv4Stack", "true")
            it.systemProperty("adb.host", providers.gradleProperty("adb.host").getOrElse("127.0.0.1"))
            it.systemProperty("adb.port", providers.gradleProperty("adb.port").getOrElse("5555"))
            it.systemProperty("adb.required", providers.gradleProperty("adb.required").getOrElse("false"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
