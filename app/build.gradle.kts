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

    // Due versioni dallo stesso codice: "free" (l'app di sempre) e "pro" (app separata, con in più
    // colori e rivestimenti liberi, il motore grafico Filament e la libreria di arredi 3D).
    // Il codice in comune sta in src/main; quello di una sola versione in src/free o src/pro.
    flavorDimensions += "edition"
    productFlavors {
        create("free") {
            dimension = "edition"
        }
        create("pro") {
            dimension = "edition"
            applicationIdSuffix = ".pro"
            versionNameSuffix = "-pro"
        }
    }

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

    // Solo nella versione pro: motore grafico Filament (Google, Apache 2.0) e caricatore di modelli glTF.
    "proImplementation"(libs.filament.android)
    "proImplementation"(libs.filament.gltfio)

    testImplementation(libs.junit)
}
