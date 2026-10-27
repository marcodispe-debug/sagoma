import javax.inject.Inject
import org.gradle.process.ExecOperations

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

/**
 * Lettura delle piantine ("Leggi la piantina da sola"): indirizzo del server (server/lettura-piantine) e chiave dell'app, dalle
 * proprietà Gradle `sagoma.lettura.url` e `sagoma.lettura.chiave`. Senza indirizzo il pulsante non c'è.
 */
val letturaUrl = providers.gradleProperty("sagoma.lettura.url").orNull?.trim().orEmpty()
val letturaChiave = providers.gradleProperty("sagoma.lettura.chiave").orNull?.trim().orEmpty()
if (letturaUrl.isNotEmpty()) require(letturaUrl.startsWith("https://")) { "sagoma.lettura.url deve iniziare con https://: $letturaUrl" }

/** Versione del catalogo sul server (la stessa usata da `:catalogo:preparaCatalogoRemoto`): l'app porta con sé quel manifest. */
val catalogoVersione = providers.gradleProperty("sagoma.catalogo.versione").orElse("1")

if (catalogoRemoto) {
    // Solo HTTPS: l'impronta di ogni file la dà il manifest, e il manifest aggiornato arriva dalla rete.
    for (u in listOf(catalogoManifest, catalogoBlobs)) {
        require(u.startsWith("https://")) { "sagoma.catalogo.manifest e sagoma.catalogo.blobs devono iniziare con https://: $u" }
    }
}

/**
 * Quello che resta nell'app quando il catalogo è sul server: elenchi, miniature e viste dall'alto. Le due luci ambiente che il 3D
 * usa (giorno e tramonto, vedi FilamentSceneRenderer) le aggiunge [LuceTelefono], già pronte. La luce "interno" non la usa
 * nessuno: resta sul server.
 */
val catalogoLeggero = listOf(
    "furniture/catalog.json", "furniture/*.png", "materials/materials.json", "materials/*_thumb.jpg",
)

/** Le luci ambiente che l'app porta con sé (assets/env/<nome>.ibl). */
val luciTelefono = listOf("giorno", "tramonto")

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

/**
 * Luci ambiente già pronte per il telefono (tools/furniture/PrefilterEnv.java): cubo da 256 px invece di 512 e tutti i livelli
 * dei riflessi già calcolati, nel formato della texture. Così aprendo il 3D l'app non li calcola più sul processore (prima lo
 * faceva Filament sul thread principale) e i file sono più piccoli (2 MB invece di 9,4 l'uno). Stesso percorso del catalogo:
 * se manca, l'app usa quello del server (versione 1) e lo prefiltra lei come prima.
 */
abstract class LuceTelefono @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tool: RegularFileProperty

    @get:Input
    abstract val java: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun write() {
        val dir = output.get().dir("env").asFile
        dir.deleteRecursively()
        dir.mkdirs()
        for (f in sources.files) {
            exec.exec { commandLine(java.get(), tool.get().asFile.path, f.path, File(dir, f.name).path, "256") }
        }
    }
}

val luceTelefonoTask = tasks.register<LuceTelefono>("luceTelefono") {
    sources.from(luciTelefono.map { catalogoRoot.file("env/$it.ibl") })
    tool.set(rootProject.layout.projectDirectory.file("tools/furniture/PrefilterEnv.java"))
    java.set(File(System.getProperty("java.home"), "bin/java").path)
    output.set(layout.buildDirectory.dir("generated/luce-telefono"))
}

/**
 * Il manifest del catalogo che l'app porta con sé (assets/remote/manifest.json), scritto dallo stesso strumento che prepara
 * la cartella per il server: con gli stessi file e la stessa versione coincidono byte per byte. Così l'app sa fin dal primo
 * avvio, anche senza rete, quali file esistono e che impronta devono avere.
 */
abstract class ManifestoCatalogo @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tool: RegularFileProperty

    /** Elenco dei materiali 2K e 4K, che stanno solo sul server (catalogo/materiali-hd.tsv; può mancare). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val hdIndex: ConfigurableFileCollection

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val java: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun write() {
        exec.exec {
            val index = hdIndex.files.firstOrNull { it.isFile }?.let { listOf("--indice-hd=${it.path}") }.orEmpty()
            commandLine(listOf(java.get(), tool.get().asFile.path, "--solo-manifest") + index + listOf(assets.get().asFile.path, output.get().dir("remote").asFile.path, version.get()))
        }
    }
}

val catalogoManifestTask = tasks.register<ManifestoCatalogo>("manifestoCatalogo") {
    assets.set(catalogoRoot)
    tool.set(rootProject.layout.projectDirectory.file("tools/remote/BuildRemoteCatalog.java"))
    hdIndex.from(rootProject.layout.projectDirectory.file("catalogo/materiali-hd.tsv"))
    version.set(catalogoVersione)
    java.set(File(System.getProperty("java.home"), "bin/java").path)
    output.set(layout.buildDirectory.dir("generated/catalogo-manifest"))
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
        // Vuoto = niente "Leggi la piantina da sola" (vedi MainActivity).
        resValue("string", "lettura_url", letturaUrl)
        resValue("string", "lettura_chiave", letturaChiave)
    }

    // Una sola app per privati e professionisti: cosa è sbloccato lo decidono i diritti dell'account
    // (core, pacchetto `access`), non varianti di build separate.

    buildTypes {
        // Per provare sul telefono com'è davvero veloce: come debug (stessa firma, si installa sopra senza perdere i progetti) ma
        // non "debuggable", che rallenta molto Compose, e solo con le librerie per i telefoni a 64 bit (niente emulatori: ~18 MB in
        // meno). gradlew :app:installVeloce (o assembleVeloce → app/build/outputs/apk/veloce/app-veloce.apk).
        create("veloce") {
            initWith(getByName("debug"))
            isDebuggable = false
            ndk { abiFilters += "arm64-v8a" }
            matchingFallbacks += "debug"
        }
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
            variant.sources.assets?.addGeneratedSourceDirectory(catalogoManifestTask, ManifestoCatalogo::output)
            variant.sources.assets?.addGeneratedSourceDirectory(luceTelefonoTask, LuceTelefono::output)
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
