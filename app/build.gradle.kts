plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sagoma.planimetria"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sagoma.planimetria"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    // Una sola app per privati e professionisti: cosa è sbloccato lo decidono i diritti dell'account
    // (core, pacchetto `access`), non varianti di build separate.

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Catalogo di arredi, materiali e luci: sta nell'asset pack "catalogo" (modulo :catalogo), installato con l'app ma
    // fuori dal pacchetto principale, così il bundle per Google Play rispetta il limite di dimensione. Nell'app installata
    // i file si leggono con l'AssetManager come prima.
    assetPacks += ":catalogo"

    // Gli APK costruiti direttamente (assembleDebug, installDebug, Android Studio) non contengono gli asset pack: in debug
    // il catalogo si aggiunge agli asset dell'app, così si prova come sempre.
    sourceSets.getByName("debug").assets.directories += "../catalogo/src/main/assets"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    // Motore grafico Filament (Google, Apache 2.0) e caricatore di modelli glTF.
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)

    testImplementation(libs.junit)
}
