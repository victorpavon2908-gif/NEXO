import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun rawSetting(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

fun normalizeSetting(value: String?): String = value
    .orEmpty()
    .trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")
    .trim()

fun buildConfigString(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

val supabaseUrl = normalizeSetting(rawSetting("SUPABASE_URL")).trimEnd('/')
val supabaseKey = normalizeSetting(rawSetting("SUPABASE_PUBLISHABLE_KEY"))
    .ifBlank { normalizeSetting(rawSetting("SUPABASE_ANON_KEY")) }
val turnUrls = normalizeSetting(rawSetting("NEXO_TURN_URLS"))
val turnUsername = normalizeSetting(rawSetting("NEXO_TURN_USERNAME"))
val turnCredential = normalizeSetting(rawSetting("NEXO_TURN_CREDENTIAL"))

android {
    namespace = "ni.nexo.app"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "ni.nexo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.0.0-rc10"

        buildConfigField("String", "SUPABASE_URL", buildConfigString(supabaseUrl))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString(supabaseKey))
        buildConfigField("String", "NEXO_TURN_URLS", buildConfigString(turnUrls))
        buildConfigField("String", "NEXO_TURN_USERNAME", buildConfigString(turnUsername))
        buildConfigField("String", "NEXO_TURN_CREDENTIAL", buildConfigString(turnCredential))
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.3.0")

    // WebRTC real para llamadas de audio y video. El SDK publica org.webrtc.*.
    implementation("io.github.webrtc-sdk:android:144.7559.15")

    implementation("com.android.billingclient:billing-ktx:9.1.0")

    implementation("io.coil-kt.coil3:coil-compose:3.6.2")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
