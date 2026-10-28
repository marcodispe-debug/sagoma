package com.sagoma.planimetria.testi.fr

import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloFr : TestiControllo() {
    override val voceMenu: String get() = "🔍 Vérifier le plan…"
    override val titolo: String get() = "Vérifier le plan"
    override val nessunProblema: String get() = "Aucun problème trouvé."
    override val cosaControlla: String get() =
        "Cherche les pièces superposées ou presque collées, les murs qui se croisent, les portes et fenêtres en double, plus larges ou plus hautes que le mur, " +
            "les pièces sans porte, les vantaux qui heurtent quelque chose en s'ouvrant, la porte d'entrée, les escaliers, les installations et les minimums légaux des pièces (fenêtres, hauteurs, mètres carrés)."
    override val spiega: String get() = "Ils peuvent fausser surfaces, métré et 3D. « Afficher » y amène et le sélectionne."
    override val daCorreggere: String get() = "À corriger"
    override val daControllare: String get() = "À vérifier"
    override val daNorme: String get() = "Règles pour le logement (Italie)"
    override val spiegaNorme: String get() =
        "Minimums du décret italien du 5 juillet 1975 pour les pièces à vivre (chambres, séjour, cuisine, bureau) et les salles de bain ; le règlement de la commune " +
            "peut demander plus. Les fenêtres comptent si elles s'ouvrent et donnent dehors, mesurées sur la baie du mur. Si vous placez des prises, " +
            "aussi les points de prise et lumineux d'une installation électrique de base (CEI 64-8, niveau 1)."
    override fun nome(n: String): String = "« $n »"
    override val mostra: String get() = "Afficher"
    override val chiudi: String get() = "Fermer"
    override fun troppi(n: Int): String = "Seulement les $n premiers : corrigez-les et vérifiez à nouveau."
    override val primaDiEsportareTitolo: String get() = "Avant d'exporter"
    override fun primaDiEsportare(n: Int): String =
        "Le plan a ${if (n == 1) "une erreur" else "$n erreurs"} de dessin (⚠) : mesures, surfaces et métré pourraient être faux aussi dans le fichier exporté."
    override val controlla: String get() = "Vérifier"
    override val esportaLoStesso: String get() = "Exporter quand même"
    override val condividi: String get() = "Partager"
    override fun oggetto(progetto: String?): String = "Contrôle du plan" + (progetto?.let { " · $it" } ?: "")
    override fun muriIncrociati(a: String): String = "$a : les murs se croisent et le contour se coupe lui-même, la surface et la 3D sont donc fausses. Déplacez les angles."
    override fun sovrapposte(a: String, b: String, m2: String): String = "$a et $b se superposent sur $m2 m² : cette partie compte deux fois. Déplacez un mur ou un angle."
    override fun fessura(a: String, b: String, cm: String): String =
        "$a et $b sont presque collées : il reste une fente de $cm cm entre les murs. D'habitude le mur est commun : faites glisser ce mur sur celui de l'autre pièce."
    override fun accavallati(a: String, b: String, cm: String): String =
        "$a et $b ont deux murs qui se chevauchent de $cm cm au lieu d'un mur commun : faites glisser ce mur sur celui de l'autre pièce."
    override fun aperturaDoppia(cosa: String, stanze: String): String = "Deux ouvertures au même endroit ($cosa, $stanze) : peut-être posée deux fois. Supprimez-en une."
    override fun muroZero(a: String, unisci: String): String = "$a : deux angles l'un sur l'autre (un mur de 0 cm). Avec le mur sélectionné, « $unisci » le supprime."
    override fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a : $cosa de $cm cm sur un mur de $muro cm, elle n'y tient pas."
    override fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a : $cosa monte à $cm cm du sol, mais le mur y est haut de $muro cm."
    override fun suMuroTolto(a: String, cosa: String): String = "$a : $cosa sur un mur supprimé (côté ouvert), qui n'existe pas."
    override fun senzaPorta(a: String): String = "$a n'a ni porte ni passage : par où entre-t-on ?"
    override val nessunaPorta: String get() = "Il n'y a aucune porte à cet étage : ajoutez des portes et des passages entre les pièces."
    override val senzaIngresso: String get() = "Aucune porte ne donne dehors : la porte d'entrée manque-t-elle ?"
    override fun finestraInterna(a: String, b: String): String = "$a : la fenêtre donne sur $b, pas dehors. Est-ce voulu ?"
    override fun piccolissima(a: String, m2: String): String = "$a est minuscule ($m2 m²) : est-ce voulu ou un morceau oublié ?"
    override val scalaSenzaPiano: String get() = "L'escalier monte, mais il n'y a pas d'étage au-dessus : ajoutez l'étage supérieur ou supprimez l'escalier."
    override fun scalaFuori(piano: String): String = "L'escalier arrive hors des pièces de l'étage supérieur ($piano)."
    override fun senzaCalorifero(a: String): String = "$a : aucun radiateur, alors que d'autres pièces en ont. Si elle est chauffée autrement, c'est bon."
    override fun senzaAcqua(a: String): String = "$a : aucun point d'eau, alors que d'autres pièces en ont."
    override fun scalaRipida(alzata: String, pedata: String?): String =
        "Escalier raide : contremarches de $alzata cm" + (pedata?.let { " et girons de $it cm" } ?: "") +
            ". Pour monter confortablement, la contremarche fait d'habitude 18-20 cm au plus et deux contremarches plus un giron font 62-64 cm : ajoutez des marches ou choisissez une pente plus douce (Infos de l'escalier → Marches)."
    override fun scalaStretta(larga: String): String =
        "Escalier large de $larga cm : dans un logement il en faut d'habitude au moins 80 (selon le règlement d'urbanisme local)."
    override fun scalaTestaBassa(h: String): String =
        "Au-dessus de l'escalier il reste $h cm jusqu'au plancher de l'étage supérieur : on se cogne la tête (il en faut au moins 200). Laissez plus de marches découvertes (Infos de l'escalier → Étage sup.)."
    override fun anteSiUrtano(stanze: String, cosa: String, cosa2: String): String =
        "$stanze : deux vantaux se heurtent en s'ouvrant ($cosa et $cosa2). Changez le côté des paumelles ou le sens d'ouverture."
    override fun antaUrta(a: String, cosa: String, cosa2: String): String =
        "$a : en s'ouvrant, le vantail heurte $cosa2 ($cosa). Déplacez $cosa2 ou changez le côté des paumelles ou le sens d'ouverture."
    override fun antaUrtaMuro(a: String, cosa: String): String =
        "$a : le vantail ne s'ouvre pas complètement, il heurte un mur ou un poteau ($cosa). Déplacez l'ouverture ou changez le côté des paumelles ou le sens d'ouverture."
    override fun pocaLuce(a: String, fin: String, pav: String, frazione: String, serve: String): String =
        "$a : fenêtres ouvrantes de $fin m² pour $pav m² de sol ($frazione). Une pièce à vivre demande au moins 1/8, soit $serve m²."
    override fun senzaFinestre(a: String, serve: String): String =
        "$a n'a pas de fenêtre ouvrante donnant dehors : une pièce à vivre a besoin de lumière et d'air directs, avec au moins $serve m² de fenêtres (1/8 du sol)."
    override val nessunaFinestra: String get() = "Il n'y a aucune fenêtre à cet étage : ajoutez-les, puis la lumière et l'air des pièces se vérifient ici."
    override fun soffittoBasso(a: String, h: String): String = "$a : plafond à $h m ; les pièces à vivre demandent au moins 2,70 m."
    override fun soffittoBassoServizi(a: String, h: String): String = "$a : plafond à $h m ; salles de bains et couloirs demandent au moins 2,40 m."
    override fun cameraPiccola(a: String, m2: String): String = "$a : $m2 m² ; une chambre doit en avoir au moins 9 (14 pour deux personnes)."
    override fun cameraPerDuePiccola(a: String, m2: String): String = "$a : $m2 m² avec des lits pour deux personnes ; une chambre pour deux doit en avoir au moins 14."
    override fun soggiornoPiccolo(a: String, m2: String): String = "$a : $m2 m² ; le séjour doit en avoir au moins 14."
    override fun bagnoSenzaFinestra(a: String): String =
        "$a : aucune fenêtre ouvrante vers l'extérieur ; la salle de bain a besoin d'une extraction mécanique (et pas d'appareils à flamme nue)."
    override fun bagnoSuCucina(bagno: String, stanza: String): String =
        "$bagno donne directement sur $stanza : beaucoup de règlements d'hygiène communaux demandent un sas ou un dégagement entre la salle de bain et la cuisine ou le séjour."
    override fun bagnoIncompleto(a: String, manca: String): String =
        "$a : sans $manca. Au moins une salle de bain du logement doit avoir WC, bidet, baignoire ou douche et lavabo."
    override val vaso: String get() = "WC"
    override val bide: String get() = "bidet"
    override val vascaODoccia: String get() = "baignoire ou douche"
    override val lavabo: String get() = "lavabo"
    override fun casaPiccola(m2: String, persone: Int, min: String): String =
        "Le logement a $m2 m² pour $persone personnes (comptées d'après les lits) ; il en faut au moins $min (14 m² par personne pour les 4 premières, 10 pour les suivantes)."
    override fun monolocalePiccolo(m2: String, min: String, perDue: Boolean): String =
        "Studio de $m2 m² : il en faut au moins $min pour ${if (perDue) "deux personnes" else "une personne"}, salle de bain comprise."
    override fun impiantoScarso(a: String, prese: String, luci: String, minPrese: String, minLuci: String): String =
        "$a : $prese et $luci. Pour une installation électrique de base (norme italienne CEI 64-8, niveau 1) il faut au moins $minPrese et $minLuci."
    override fun puntiPresa(n: Int): String = when (n) {
        0 -> "aucun point de prise"
        1 -> "1 point de prise"
        else -> "$n points de prise"
    }
    override fun puntiLuce(n: Int): String = when (n) {
        0 -> "aucun point lumineux"
        1 -> "1 point lumineux"
        else -> "$n points lumineux"
    }
}
