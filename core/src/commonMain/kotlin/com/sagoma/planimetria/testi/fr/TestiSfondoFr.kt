package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiSfondo

object TestiSfondoFr : TestiSfondo() {
    override val impossibileLeggereFile: String get() = "Impossible de lire le fichier"
    override val nonSpazioSalvareSfondo: String get() = "Pas assez d'espace pour enregistrer le fond : essayez une image plus petite"
    override val ricalcaPiantina: String get() = "Décalquer un plan"
    override val piantaSfondo: String get() = "Plan de fond"
    override fun guidaRicalcoPasso(number: Any?, total: Any?): String = "Guide du décalque · étape $number sur $total"
    override val caricaPiantinaHaiFoto: String get() = "Chargez le plan que vous avez (photo, scan ou PDF du cadastre ou du projet)."
    override val caricaPiantinaHaiImmagine: String get() = "Chargez le plan que vous avez sous forme d'image (photo, scan, JPG ou PNG). Pour un PDF, enregistrez sa page en image."
    override val tocchiDueEstremiMisura: String get() =
        "Puis touchez les deux extrémités d'une mesure connue et tapez sa longueur : le plan est mis à l'échelle. " +
            "Enfin, choisissez comment dessiner les pièces sur le plan : avec une forme ou avec « Mur par mur », angle après angle."
    override val mettiSottoDisegnoPianta: String get() =
        "Placez un plan existant sous le dessin (photo, scan ou PDF du cadastre ou du projet), " +
            "étalonnez-le sur une mesure connue et décalquez les pièces par-dessus."
    override val fotoImmagine: String get() = "Photo / image"
    override val altraImmagine: String get() = "Autre image"
    override val pdf: String get() = "PDF"
    override val caricamento: String get() = "Chargement…"
    override val visibile: String get() = "Visible"
    override fun trasparenza(p1: Any?): String = "Transparence : $p1 %"
    override fun scalaAttuale1Px(p1: Any?, p2: Any?, p3: Any?): String = "Échelle actuelle : 1 px = $p1 cm · image $p2 × $p3 m"
    override val tara: String get() = "📏 Étalonner"
    override val sposta: String get() = "✋ Déplacer"
    override val togliSfondo: String get() = "Retirer le fond"
    override val trascinaDitoSpostarePianta: String get() = "Faites glisser un doigt pour déplacer le plan de fond sous le dessin."
    override val taraturaToccaInizioMisura: String get() = "Étalonnage : touchez le début d'une mesure connue (ex. un mur coté)."
    override val oraToccaFineMisura: String get() = "Touchez maintenant la fin de la mesure. Zoomez à deux doigts pour être précis."
    override val scriviMisuraRealeB: String get() = "Tapez la longueur réelle entre A et B."
    override val misuraReale: String get() = "Longueur réelle"
    override val quantoLungaDavveroDistanza: String get() = "Quelle est la longueur réelle entre A et B ?"
    override val piuMisuraLungaPiu: String get() = "Plus la mesure est longue, plus l'étalonnage est précis."
    override val tara2: String get() = "Étalonner"
    override val rifai: String get() = "Refaire"
    override val leggiDaSola: String get() = "Lire le plan pour moi"
    override val leggiDaSolaSpiega: String get() = "Nouveau, gratuit : chargez le plan et Sagoma dessine pour vous les pièces, les portes et les fenêtres. Ensuite, vérifiez et corrigez."
    override val letturaInCorso: String get() = "Lecture du plan… cela peut prendre une minute."
    override fun piantinaLetta(stanze: Int): String = "Plan lu : $stanze pièces. Vérifiez les mesures et corrigez si besoin."
    override val senzaMisureScritte: String get() = "Aucune cote écrite sur le plan : l'échelle est approximative, vérifiez-la."
    override val letturaNonRiuscita: String get() = "Impossible de lire le plan : décalquez-le à la main."
    override val letturaNonRaggiungibile: String get() = "Le service de lecture ne répond pas (internet nécessaire) : décalquez le plan à la main."
    override val troppeLetture: String get() = "Vous avez déjà lu beaucoup de plans aujourd'hui : réessayez demain ou décalquez-le à la main."
    override val nonUnaPiantina: String get() = "L'image ne ressemble pas à un plan : décalquez-le à la main ou essayez une autre image."
    override val dxf: String get() = "📐 DXF (CAO)"
    override val dxfDwg: String get() = "Les fichiers DWG ne s'ouvrent pas : enregistrez le plan en DXF depuis votre logiciel de CAO."
    override val dxfBinario: String get() = "Ce DXF est binaire : enregistrez-le en DXF texte (ASCII) depuis votre logiciel de CAO."
    override val dxfVuoto: String get() = "Il n'y a rien à dessiner dans ce fichier DXF (ou tous ses calques sont éteints)."
    override fun dxfInScala(larghezza: String, altezza: String): String = "Plan DXF chargé déjà à l'échelle ($larghezza × $altezza m) : pas d'étalonnage."
    override fun dxfUnita(cmPerUnita: Double): String =
        "Le fichier n'indique pas son unité : je l'ai lu en " + when (cmPerUnita) { 100.0 -> "mètres"; 0.1 -> "millimètres"; else -> "centimètres" } +
            ". Si les mesures ne sont pas bonnes, utilisez «Étalonner»."
    override val dxfSpiega: String get() = "DXF d'AutoCAD ou d'un autre logiciel de CAO (par exemple le plan de l'architecte) : déjà à l'échelle, sans étalonnage."
    override val dxfParziale: String get() = "Dessin très grand : seule une partie des lignes a été chargée."
}
