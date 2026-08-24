plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termdroid.agent"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":core"))
    implementation(libs.anthropic.java)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // org.json en la JVM: en tests unitarios el android.jar es un stub que lanza.
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
