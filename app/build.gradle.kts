import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

// Clé de signature release : clé debug locale par défaut (installation facile sur la TV, build NON distribuable),
// surchargeable par l'environnement (OPENCTV_RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD) ou
// `local.properties` (openctv.release.store_file / ...). Aucun secret n'est versionné ni journalisé.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun releaseSigningValue(name: String): String? =
    System.getenv("OPENCTV_RELEASE_$name") ?: localProperties.getProperty("openctv.release.${name.lowercase()}")

val releaseStoreFile: String? = releaseSigningValue("STORE_FILE")

android {
    namespace = "com.openctv.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.openctv.browser"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseSigningValue("STORE_PASSWORD")
                keyAlias = releaseSigningValue("KEY_ALIAS")
                keyPassword = releaseSigningValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Identifiant propre au debug (com.openctv.browser.dev) : installable à côté de la release, données séparées.
        // Le libellé « OpenCTV Dev » vient de app/src/debug/res/values/strings.xml.
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    }
}

kotlin {
    jvmToolchain(17)
}

// Schémas Room versionnés (app/schemas/) : chaque version de la base est figée, et MigrationSchemaTest vérifie
// que les migrations mènent exactement au schéma suivant (les favoris d'une installation existante sont préservés).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.koin.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.webkit)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
