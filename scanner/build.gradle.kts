plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

// Scansione delle stanze con la realtà aumentata (ARCore): solo Android, senza Compose. Qui stanno la sessione AR, il
// disegno della fotocamera e dei piani rilevati e il tocco sul pavimento; i dati e la geometria (centimetri di Sagoma,
// chiusura del perimetro) restano nel `core`, in Kotlin puro e senza ARCore. La schermata (Compose) sta in `ui`.
kotlin {
    android {
        namespace = "com.sagoma.planimetria.scanner"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        androidMain.dependencies {
            api(project(":core"))
            implementation(libs.arcore)
        }
    }
}
