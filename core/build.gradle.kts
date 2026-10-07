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
