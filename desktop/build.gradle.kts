import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// Sagoma per computer (Windows, Mac, Linux): la stessa interfaccia dell'app, pensata per i professionisti
// che lavorano con mouse e tastiera.
kotlin {
    jvm()
    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Prove della vista 3D anche su Linux (nel cloud, con Xvfb e Mesa): l'app per Windows non cambia.
            runtimeOnly("org.lwjgl:lwjgl:3.4.3:natives-linux")
            runtimeOnly("org.lwjgl:lwjgl-opengl:3.4.3:natives-linux")
        }
        jvmMain.dependencies {
            implementation(project(":ui"))
            implementation(compose.desktop.currentOs)
            // Il "thread principale" delle coroutine sul computer è quello di Swing.
            implementation(libs.kotlinx.coroutines.swing)
            // PDF (catasto, progetto) come pianta di sfondo: la prima pagina diventa un'immagine (Apache PDFBox, licenza Apache 2.0).
            implementation(libs.pdfbox)
            // Vista 3D: OpenGL con LWJGL (licenza BSD), disegnata fuori schermo. Il contesto si crea con le
            // funzioni di Windows (niente GLFW: la sua DLL non firmata può essere bloccata da Smart App Control).
            val lwjgl = "3.4.3"
            val natives = "natives-windows"
            implementation("org.lwjgl:lwjgl:$lwjgl")
            implementation("org.lwjgl:lwjgl-opengl:$lwjgl")
            runtimeOnly("org.lwjgl:lwjgl:$lwjgl:$natives")
            runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjgl:$natives")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.sagoma.planimetria.desktop.MainKt"
        // Durante lo sviluppo gli arredi e i materiali si leggono dalla cartella del catalogo.
        jvmArgs += "-Dsagoma.assets=${rootProject.file("catalogo/src/main/assets").absolutePath}"
        // Catalogo sul server (docs/catalogo-server.md), gli stessi indirizzi dell'app: da lì arrivano i materiali 2K, e quello che
        // non c'è nella cartella del catalogo. Senza indirizzi niente rete, e i materiali restano quelli 1K.
        for ((gradle, system) in listOf("sagoma.catalogo.manifest" to "sagoma.remote.manifest", "sagoma.catalogo.blobs" to "sagoma.remote.blobs")) {
            providers.gradleProperty(gradle).orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { jvmArgs += "-D$system=$it" }
        }
        // "Leggi la piantina da sola": indirizzo del server e chiave dell'app dalle proprietà Gradle (vuoto = niente pulsante).
        for (key in listOf("sagoma.lettura.url", "sagoma.lettura.chiave")) {
            providers.gradleProperty(key).orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { jvmArgs += "-D$key=$it" }
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Sagoma"
            packageVersion = "1.0.0"
            // Icone del programma installato (dal marchio, come l'icona della finestra in Marchio.kt).
            windows { iconFile.set(project.file("icone/sagoma.ico")) }
            macOS { iconFile.set(project.file("icone/sagoma.icns")) }
            linux { iconFile.set(project.file("icone/sagoma.png")) }
        }
    }
}
