plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.scanforge.core.ocr"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Background OCR that survives app death + Hilt-injected workers.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // ML Kit Text Recognition v2 — on-device, bundled script models (Latin + Devanagari + CJK).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented ML Kit test on a real device/emulator (renders a Hindi+English sample bitmap).
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
