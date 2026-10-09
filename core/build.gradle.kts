plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Modello della casa, geometria (piante, quote, 3D, computo), editor e formato dei file: Kotlin puro,
// senza Android, così lo stesso codice gira nell'app Android, sul computer, nel browser e più avanti su iPhone.
kotlin {
    android {
        namespace = "com.sagoma.planimetria.core"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // api: i tipi @Serializable della casa li usano anche i moduli che dipendono da core.
            api(libs.kotlinx.serialization.json)
            // L'editor (EditorViewModel) usa ViewModel e coroutine, disponibili su tutte le piattaforme.
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
        }
        // Codice con i file di Java (archivio dei progetti su disco): in comune tra Android e computer.
        val jvmShared by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmShared)
        jvmMain.get().dependsOn(jvmShared)
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(kotlin("test"))
        }
    }
}

// Analisi offline di una registrazione di scansione (formato JSONL v1, vedi core/.../scan/recording): senza telefono né Android.
//   ./gradlew :core:analyzeRecording -Prec=<file.jsonl> [-Pout=<cartella>] [-Pmin=<secondi>] [-PnoPoints]
// Percorsi assoluti. Scrive report (tutti / 1 s / 5 s / 10 s), la vista dall'alto in SVG, planes.csv. Il file NON va aggiunto al repository.
tasks.register<JavaExec>("analyzeRecording") {
    group = "verification"
    description = "Analizza una registrazione di scansione (-Prec=file.jsonl) e scrive report e SVG dall'alto."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.recording.analysis.AnalyzeRecordingCli")
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
        providers.gradleProperty("min").orNull?.let { "--min-persistence=$it" },
        if (providers.gradleProperty("noPoints").isPresent) "--no-points" else null,
    )
}

// Room Reconstruction R1/R2 (offline): da una registrazione M0.2 (ZIP o cartella) alla mappa 3D e alle superfici candidate.
//   ./gradlew :core:reconstruct -Prec=<file.zip> [-Pout=<cartella>] [-PnoStability]
// Scrive report.txt, surfaces.csv, map-topdown.svg, map.ply, elevation-S*.svg, overlay-*.svg. Il dataset non viene modificato.
tasks.register<JavaExec>("reconstruct") {
    group = "verification"
    description = "Ricostruzione R1/R2 di una registrazione M0.2 (-Prec=file.zip): mappa, superfici, report e viste."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.reconstruction.ReconstructCli")
    maxHeapSize = "3g"
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
        if (providers.gradleProperty("noStability").isPresent) "--no-stability" else null,
    )
}

// M4 QualityEngine (offline): qualità della ricostruzione (misura, completezza, evidenza alternativa, evidenza, difetti).
//   ./gradlew :core:quality -Prec=<file.zip> [-Pout=<cartella>]
// Scrive quality-report.json, wall-quality.csv, room-quality.csv, quality-defects.csv, quality-summary.txt, quality-topdown.svg,
// quality-timing.txt. Non scrive né modifica i report di :core:reconstruct. Nessuna decisione di rescansione.
tasks.register<JavaExec>("quality") {
    group = "verification"
    description = "M4 QualityEngine su una registrazione M0.2 (-Prec=file.zip): qualità per parete e per stanza, difetti."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.reconstruction.quality.QualityCli")
    maxHeapSize = "3g"
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
    )
}

// M5 RescanDirector (offline): dai difetti di M4 a richieste di nuova acquisizione localizzate e ordinate, o NO_RESCAN_NEEDED.
//   ./gradlew :core:rescan -Prec=<file.zip> [-Pout=<cartella>]
// Scrive rescan-report.json, rescan-requests.csv, rescan-summary.txt, rescan-topdown.svg, rescan-timing.txt. Non tocca altri report.
tasks.register<JavaExec>("rescan") {
    group = "verification"
    description = "M5 RescanDirector su una registrazione M0.2 (-Prec=file.zip): richieste di nuova acquisizione o NO_RESCAN_NEEDED."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.reconstruction.rescan.RescanCli")
    maxHeapSize = "3g"
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
    )
}

// M5.1 RescanSession (offline): memoria tra scansioni dei bersagli delle richieste M5 (stessa registrazione continuata).
//   ./gradlew :core:rescanSession -Prec=<registrazione corrente.zip> [-Psession=<rescan-session.json>] [-Pout=<cartella>]
// Scrive rescan-session.json, rescan-session-summary.txt, rescan-session-events.csv. Non tocca altri report.
tasks.register<JavaExec>("rescanSession") {
    group = "verification"
    description = "M5.1 RescanSession: aggiorna la sessione con la registrazione corrente (-Prec=file.zip, -Psession=json precedente)."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.reconstruction.rescan.session.RescanSessionCli")
    maxHeapSize = "3g"
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("session").orNull?.let { "--session=$it" },
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
    )
}

// Esperimento M3.1 (offline): dalle candidate di parete di una registrazione al perimetro, con test sull'ordine e sulle soglie.
//   ./gradlew :core:perimeterExperiment -Prec=<file.jsonl> [-Pout=<cartella>] [-Pperm=20] [-Pcandidates=<file.json>]
tasks.register<JavaExec>("perimeterExperiment") {
    group = "verification"
    description = "Esperimento M3.1: perimetro dalle candidate di parete di una registrazione (-Prec=file.jsonl)."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.experiment.PerimeterExperimentCli")
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
        providers.gradleProperty("perm").orNull?.let { "--permutations=$it" },
        providers.gradleProperty("candidates").orNull?.let { "--candidates=$it" },
    )
}

// Simulazione M2.0 della scansione assistita su una registrazione (offline, senza telefono).
//   ./gradlew :core:assistedSimulation -Prec=<file.jsonl> [-Pout=<cartella>]
tasks.register<JavaExec>("assistedSimulation") {
    group = "verification"
    description = "Simulazione M2.0: candidate di parete proposte dal motore assistito su una registrazione (-Prec=file.jsonl)."
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn("jvmMainClasses")
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("com.sagoma.planimetria.scan.assisted.AssistedSimulationCli")
    args = listOfNotNull(
        providers.gradleProperty("rec").orNull,
        providers.gradleProperty("out").orNull?.let { "--out=$it" },
    )
}
