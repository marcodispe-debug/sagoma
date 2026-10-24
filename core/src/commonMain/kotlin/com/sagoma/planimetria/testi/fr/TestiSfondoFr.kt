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
            "Enfin, décalquez les murs avec « Mur par mur », angle après angle."
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
}
