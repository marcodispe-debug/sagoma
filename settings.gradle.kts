pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Il plugin Kotlin/Wasm aggiunge i propri repository (Node.js, Binaryen) al progetto principale.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sagoma"
include(":app")
// Codice condiviso tra Android, web e (in futuro) iPhone: modello della casa, geometria, formato dei file.
include(":core")
// Interfaccia (Compose Multiplatform), uguale su Android, computer e browser.
include(":ui")
// App per computer e sito web.
include(":desktop")
include(":web")
