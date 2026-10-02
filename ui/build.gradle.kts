plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Tutta l'interfaccia dell'editor in Compose Multiplatform: la stessa su Android, sul computer e nel
// browser. Quello che cambia da una piattaforma all'altra passa dall'interfaccia `Platform`.
kotlin {
    android {
        namespace = "com.sagoma.planimetria.ui"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            api(libs.cmp.lifecycle.viewmodel.compose)
            api(libs.cmp.lifecycle.runtime.compose)
        }
        // Computer e browser disegnano con Skia: immagini e PDF in comune.
        val skikoMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(skikoMain)
        wasmJsMain.get().dependsOn(skikoMain)
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Skia per disegnare nelle prove (immagini di controllo della pianta).
            implementation(compose.desktop.currentOs)
            // "Thread principale" delle coroutine (quello di Swing), per provare l'editor come sul computer.
            implementation(libs.kotlinx.coroutines.swing)
        }
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            api(libs.androidx.core.ktx)
        }
    }
}
