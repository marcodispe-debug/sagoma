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
 * Ci mette anche i materiali 2K e 4K di `hd/` (fuori dall'app e dal repository, vedi `preparaMaterialiHd`) e ne riscrive
 * l'elenco `materiali-hd.tsv`, che invece va nel repository: l'app lo legge per avere lo stesso manifest del server.
 */
val catalogoVersione = providers.gradleProperty("sagoma.catalogo.versione").orElse("1")
tasks.register<Exec>("preparaCatalogoRemoto") {
    group = "sagoma"
    description = "Prepara in build/catalogo-remoto i file da caricare sul server del catalogo"
    val assets = layout.projectDirectory.dir("src/main/assets")
    val out = layout.buildDirectory.dir("catalogo-remoto")
    val tool = rootProject.layout.projectDirectory.file("tools/remote/BuildRemoteCatalog.java")
    val hd = layout.projectDirectory.dir("hd")
    val hdIndex = layout.projectDirectory.file("materiali-hd.tsv")
    inputs.dir(assets)
    inputs.files(fileTree(hd))
    inputs.file(tool)
    inputs.property("versione", catalogoVersione)
    outputs.dir(out)
    val java = File(System.getProperty("java.home"), "bin/java").path
    commandLine(java, tool.asFile.path, "--hd=${hd.asFile.path}", "--indice-hd=${hdIndex.asFile.path}", assets.asFile.path, out.get().asFile.path, catalogoVersione.get())
}

/**
 * Materiali più definiti, solo per il server: `gradlew :catalogo:preparaMaterialiHd` scarica dalle fonti originali (ambientCG,
 * Poly Haven: CC0) i materiali di tools/furniture/materials.tsv e scrive in `hd/materials/2k/` le mappe 2K (3D del computer).
 * Con `-Psagoma.materiali.lato=4096` le 4K per i render (`hd/materials/4k/`). Riprende da dove si era fermato.
 */
tasks.register<Exec>("preparaMaterialiHd") {
    group = "sagoma"
    description = "Scarica e prepara in hd/ i materiali 2K (o 4K) per il catalogo sul server"
    val tool = rootProject.layout.projectDirectory.file("tools/furniture/PackMaterialsHd.java")
    val table = rootProject.layout.projectDirectory.file("tools/furniture/materials.tsv")
    val side = providers.gradleProperty("sagoma.materiali.lato").orElse("2048")
    val java = File(System.getProperty("java.home"), "bin/java").path
    commandLine(java, tool.asFile.path, table.asFile.path, layout.projectDirectory.dir("hd").asFile.path, side.get())
}
