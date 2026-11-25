// Cloud sign-in works only when the app owner supplies Firebase project configuration.
// Build stays usable offline when the values are absent (no simulated Google accounts).
fun androidString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val cloudWebClientId = providers.gradleProperty("sequmioGoogleWebClientId")
    .orElse(providers.environmentVariable("SEQ_GOOGLE_WEB_CLIENT_ID")).getOrElse("")
val cloudApiKey = providers.gradleProperty("sequmioFirebaseApiKey")
    .orElse(providers.environmentVariable("SEQ_FIREBASE_API_KEY")).getOrElse("")
val cloudAppId = providers.gradleProperty("sequmioFirebaseAppId")
    .orElse(providers.environmentVariable("SEQ_FIREBASE_APP_ID")).getOrElse("")
val cloudProjectId = providers.gradleProperty("sequmioFirebaseProjectId")
    .orElse(providers.environmentVariable("SEQ_FIREBASE_PROJECT_ID")).getOrElse("")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sequmio.givlse"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sequmio.givlse"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "1.2.10"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", androidString(cloudWebClientId))
        buildConfigField("String", "FIREBASE_API_KEY", androidString(cloudApiKey))
        buildConfigField("String", "FIREBASE_APP_ID", androidString(cloudAppId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", androidString(cloudProjectId))
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    // No Firebase Analytics or advertising dependency.
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    testImplementation("junit:junit:4.13.2")
    // Real JSONObject implementation for local JVM scenario parser tests.
    testImplementation("org.json:json:20240303")
}
