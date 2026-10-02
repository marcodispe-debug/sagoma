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
        }
        jvmMain.dependencies {
            implementation(project(":ui"))
            implementation(compose.desktop.currentOs)
            // Il "thread principale" delle coroutine sul computer è quello di Swing.
            implementation(libs.kotlinx.coroutines.swing)
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
        // Durante lo sviluppo gli arredi e i materiali si leggono dalla cartella della versione pro.
        jvmArgs += "-Dsagoma.assets=${rootProject.file("app/src/pro/assets").absolutePath}"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Sagoma"
            packageVersion = "1.0.0"
        }
    }
}
