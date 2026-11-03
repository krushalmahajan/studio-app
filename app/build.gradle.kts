import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun prop(name: String, default: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: default

val studioUrl = prop("studioUrl", "https://studio.example.com/")
val studioApiPath = prop("studioApiPath", "wp-content/themes/twentysixteen/mobile-app/mobile-app-api.php")

// Release signing: environment variables first (CI), then keystore.properties next to settings.gradle.kts.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
val keystoreFile = signing("storeFile", "STUDIO_KEYSTORE_FILE")

android {
    namespace = "com.idbiintech.studio.attendance"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.idbiintech.studio.attendance"
        minSdk = 24
        targetSdk = 35
        versionCode = prop("studioVersionCode", "1").toInt()
        versionName = prop("studioVersionName", "1.0.0")
        buildConfigField("String", "STUDIO_URL", "\"$studioUrl\"")
        buildConfigField("String", "API_PATH", "\"$studioApiPath\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = rootProject.file(keystoreFile)
                storePassword = signing("storePassword", "STUDIO_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "STUDIO_KEY_ALIAS")
                keyPassword = signing("keyPassword", "STUDIO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
