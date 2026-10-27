# Regole ProGuard/R8 specifiche del progetto. R8 è acceso solo nella versione di prova «veloceR8» (app/build.gradle.kts):
# le librerie usate (Filament e gltfio, kotlinx.serialization, Compose) portano già le loro regole, e l'app non usa
# riflessione. Se la prova sul telefono trova qualcosa che R8 ha tolto per sbaglio, la regola va qui.
