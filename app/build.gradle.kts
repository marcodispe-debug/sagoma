import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Catalogo scaricato a richiesta: indirizzi del manifest e della cartella dei blob (vedi docs/catalogo-server.md), dalle
 * proprietà Gradle `sagoma.catalogo.manifest` e `sagoma.catalogo.blobs` (in gradle.properties o con -P). Se mancano il
 * catalogo intero arriva con l'app nell'asset pack "catalogo".
 */
val catalogoManifest = providers.gradleProperty("sagoma.catalogo.manifest").orNull?.trim().orEmpty()
val catalogoBlobs = providers.gradleProperty("sagoma.catalogo.blobs").orNull?.trim().orEmpty()
val catalogoRemoto = catalogoManifest.isNotEmpty() && catalogoBlobs.isNotEmpty()

/** Quello che resta nell'app quando il catalogo è sul server: elenchi, miniature, viste dall'alto e luci ambiente. */
val catalogoLeggero = listOf("furniture/catalog.json", "furniture/*.png", "materials/materials.json", "materials/*_thumb.jpg", "env/*.ibl")

/** Copia nella cartella generata la parte leggera del catalogo (i modelli e le mappe dei materiali restano sul server). */
abstract class CatalogoLeggero @Inject constructor(private val fs: FileSystemOperations) : DefaultTask() {
    /** I file da copiare (servono anche a sapere quando rifare la copia). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val files: ConfigurableFileCollection

    @get:Internal
    abstract val root: DirectoryProperty

    @get:Input
    abstract val patterns: ListProperty<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        fs.sync {
            from(root) { include(patterns.get()) }
            into(output)
        }
    }
}

val catalogoRoot = rootProject.layout.projectDirectory.dir("catalogo/src/main/assets")
val catalogoLeggeroTask = tasks.register<CatalogoLeggero>("catalogoLeggero") {
    root.set(catalogoRoot)
    patterns.set(catalogoLeggero)
    files.from(catalogoRoot.asFileTree.matching { include(catalogoLeggero) })
    output.set(layout.buildDirectory.dir("generated/catalogo-leggero"))
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
        // Vuoti = catalogo remoto spento (vedi SagomaApplication).
        resValue("string", "remote_manifest_url", catalogoManifest)
        resValue("string", "remote_blob_base_url", catalogoBlobs)
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
        resValues = true
    }

    if (!catalogoRemoto) {
        // Catalogo di arredi, materiali e luci nell'asset pack "catalogo" (modulo :catalogo), installato con l'app ma fuori
        // dal pacchetto principale, così il bundle per Google Play rispetta il limite di dimensione. Nell'app installata i
        // file si leggono con l'AssetManager come prima.
        assetPacks += ":catalogo"
        // Gli APK costruiti direttamente (assembleDebug, installDebug, Android Studio) non contengono gli asset pack: in
        // debug il catalogo si aggiunge agli asset dell'app, così si prova come sempre.
        sourceSets.getByName("debug").assets.directories += "../catalogo/src/main/assets"
    }
}

// Catalogo sul server: nell'app (debug e release) solo la parte leggera; modelli e materiali si scaricano quando servono.
if (catalogoRemoto) {
    androidComponents {
        onVariants { variant ->
            variant.sources.assets?.addGeneratedSourceDirectory(catalogoLeggeroTask, CatalogoLeggero::output)
        }
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

    // Motore grafico Filament (Google, Apache 2.0) e caricatore di modelli glTF.
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)

    testImplementation(libs.junit)
}
