import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun prop(name: String, default: String): String =
    providers.gradleProperty(name).getOrElse(default)

val startUrl = prop("START_URL", "https://example.com/")
val appName = prop("APP_NAME", "Come")
val appId = prop("APP_ID", "com.sadhan.come")
val hostName = URI(startUrl).host ?: error("START_URL me valid host nahi hai")

android {
    namespace = "com.sadhan.come"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "START_URL", "\"$startUrl\"")
        buildConfigField("String", "HOST_NAME", "\"$hostName\"")
        resValue("string", "app_name", appName)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Testing ke liye debug key se sign (install ho jayega).
            // Play Store ke liye apni keystore lagao (README dekho).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.browser:browser:1.8.0")
}
