plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Modello della casa, geometria (piante, quote, 3D, computo), editor e formato dei file: Kotlin puro,
// senza Android, così lo stesso codice gira nell'app Android, sul computer, nel browser e più avanti su iPhone.
kotlin {
    android {
        namespace = "com.sagoma.planimetria.core"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // api: i tipi @Serializable della casa li usano anche i moduli che dipendono da core.
            api(libs.kotlinx.serialization.json)
            // L'editor (EditorViewModel) usa ViewModel e coroutine, disponibili su tutte le piattaforme.
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
        }
        // Codice con i file di Java (archivio dei progetti su disco): in comune tra Android e computer.
        val jvmShared by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmShared)
        jvmMain.get().dependsOn(jvmShared)
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(kotlin("test"))
        }
    }
}
