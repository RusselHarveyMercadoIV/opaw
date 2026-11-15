import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.opaw"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.opaw"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("OPAW_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("OPAW_VERSION_NAME") ?: "0.1.0"

        // The Pack's Supabase project, from gradle.properties, local.properties or the environment.
        // Left empty, the Pack shows its "almost ready" screen, as on iOS.
        fun secret(name: String): String =
            (project.findProperty(name) as String?) ?: System.getenv(name) ?: localProperty(name) ?: ""
        buildConfigField("String", "SUPABASE_HOST", "\"${secret("SUPABASE_HOST")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
        // The Web OAuth client ID from Google Cloud, the same one set on Supabase's Google provider.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
    }

    // The Play upload key, from the environment (GitHub secrets) or local.properties. Without it,
    // release builds are left unsigned and Play Console won't accept them.
    val keystore = System.getenv("OPAW_KEYSTORE_FILE") ?: localProperty("OPAW_KEYSTORE_FILE")
    // A fixed debug key, so every test build has the SHA-1 registered for Google sign-in.
    // CI writes it from the OPAW_DEBUG_KEYSTORE_BASE64 secret; otherwise the usual debug key is used.
    val debugKeystore = System.getenv("OPAW_DEBUG_KEYSTORE_FILE") ?: localProperty("OPAW_DEBUG_KEYSTORE_FILE")
    signingConfigs {
        if (debugKeystore != null) {
            getByName("debug") {
                storeFile = file(debugKeystore)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (keystore != null) {
            create("upload") {
                storeFile = file(keystore)
                storePassword = System.getenv("OPAW_KEYSTORE_PASSWORD") ?: localProperty("OPAW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OPAW_KEY_ALIAS") ?: localProperty("OPAW_KEY_ALIAS")
                keyPassword = System.getenv("OPAW_KEY_PASSWORD") ?: localProperty("OPAW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.exifinterface)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.play.services.location)
    implementation(libs.maplibre)
    implementation(libs.billing.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
}

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return Properties().apply { file.inputStream().use { load(it) } }.getProperty(name)
}
