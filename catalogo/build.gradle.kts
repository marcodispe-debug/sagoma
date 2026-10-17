plugins {
    alias(libs.plugins.android.asset.pack)
}

// Asset pack di Google Play con il catalogo (arredi, materiali, luci): src/main/assets, stessi percorsi di prima
// (furniture/, materials/, env/). "install-time": arriva insieme all'app e l'AssetManager lo vede come gli altri asset.
assetPack {
    packName.set("catalogo")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}

/**
 * Catalogo scaricato a richiesta (docs/catalogo-server.md): `gradlew :catalogo:preparaCatalogoRemoto` scrive in
 * build/catalogo-remoto la cartella da caricare sul server (manifest.json e blobs/), con la versione della proprietà
 * Gradle `sagoma.catalogo.versione`. Lo stesso strumento, con la stessa versione, dà all'app il manifest che porta con sé.
 */
val catalogoVersione = providers.gradleProperty("sagoma.catalogo.versione").orElse("1")
tasks.register<Exec>("preparaCatalogoRemoto") {
    group = "sagoma"
    description = "Prepara in build/catalogo-remoto i file da caricare sul server del catalogo"
    val assets = layout.projectDirectory.dir("src/main/assets")
    val out = layout.buildDirectory.dir("catalogo-remoto")
    val tool = rootProject.layout.projectDirectory.file("tools/remote/BuildRemoteCatalog.java")
    inputs.dir(assets)
    inputs.file(tool)
    inputs.property("versione", catalogoVersione)
    outputs.dir(out)
    val java = File(System.getProperty("java.home"), "bin/java").path
    commandLine(java, tool.asFile.path, assets.asFile.path, out.get().asFile.path, catalogoVersione.get())
}
