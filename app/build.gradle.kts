plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val geminiApiKeys = listOfNotNull(
    System.getenv("GEMINI_API_KEY"),
    System.getenv("GEMINI_API_KEY_2"),
    System.getenv("GEMINI_API_KEY_3")
).flatMap { it.split(",", "\n", ";") }
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .joinToString(",")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val buildRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
val releaseStorePath = System.getenv("RELEASE_STORE_FILE")
val hasReleaseSigning = !releaseStorePath.isNullOrBlank() && file(releaseStorePath).exists()

android {
    namespace = "com.jamal.kucingjamal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jamal.kucingjamal"
        minSdk = 26
        targetSdk = 35
        versionCode = buildRunNumber
        versionName = "1.0.$buildRunNumber"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKeys\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "kucingjamal123"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "jamal"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "kucingjamal123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("wav", "webp")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
