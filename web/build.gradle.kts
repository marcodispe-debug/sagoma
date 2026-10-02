plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Sagoma nel browser (Kotlin/Wasm): la stessa interfaccia dell'app, senza installare nulla.
// Sviluppo: ./gradlew :web:wasmJsBrowserDevelopmentRun   ·   Pubblicazione: :web:wasmJsBrowserDistribution
kotlin {
    wasmJs {
        outputModuleName = "sagoma"
        browser {
            commonWebpackConfig { outputFileName = "sagoma.js" }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":ui"))
        }
    }
}
