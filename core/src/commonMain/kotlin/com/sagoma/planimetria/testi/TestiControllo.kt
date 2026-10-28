package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.FixSummary

/** «Controlla la pianta»: errori di disegno, cose da controllare e norme per la casa. I nomi delle stanze arrivano già passati da [nome]. */
open class TestiControllo {
    open val voceMenu: String get() = "🔍 Controlla la pianta…"
    open val titolo: String get() = "Controlla la pianta"
    open val nessunProblema: String get() = "Nessun problema trovato."
    open val cosaControlla: String get() =
        "Cerca stanze sovrapposte o quasi attaccate, muri che si incrociano, porte e finestre doppie, più larghe del muro o più alte, " +
            "stanze senza porta, ante che urtano aprendosi, la porta d'ingresso, le scale, gli impianti e i minimi di legge delle stanze (finestre, altezze, metri quadri)."
    open val spiega: String get() =
        "Possono sbagliare metri quadri, computo e 3D. Sulla pianta un «!» segna dov'è ogni problema. «Mostra» porta lì e lo seleziona; " +
            "«Correggi» lo sistema da solo (si annulla con ↶); «Togli dalla pianta» toglie il suo «!»."
    open val daCorreggere: String get() = "Da correggere"
    open val daControllare: String get() = "Da controllare"
    open val daNorme: String get() = "Norme per la casa (Italia)"
    open val spiegaNorme: String get() =
        "Minimi del DM 5 luglio 1975 per le stanze da abitare (camere, soggiorno, cucina, studio) e i bagni; il regolamento edilizio del comune " +
            "può chiedere di più. Le finestre contano se si aprono e danno fuori, misurate sul vano del muro. Se metti le prese, anche i punti presa " +
            "e luce dell'impianto elettrico di base (CEI 64-8, livello 1)."
    /** Nome di una stanza dentro un testo. */
    open fun nome(n: String): String = "«$n»"
    open val mostra: String get() = "Mostra"
    open val chiudi: String get() = "Chiudi"
    open fun troppi(n: Int): String = "Solo i primi $n: correggi questi e controlla di nuovo."
    open val primaDiEsportareTitolo: String get() = "Prima di esportare"
    open fun primaDiEsportare(n: Int): String =
        "La pianta ha ${if (n == 1) "un errore" else "$n errori"} di disegno (⚠): misure, metri quadri e computo potrebbero essere sbagliati anche nel file esportato."
    open val controlla: String get() = "Controlla"
    open val esportaLoStesso: String get() = "Esporta lo stesso"
    open val correggi: String get() = "Correggi"
    open val nonSiCorregge: String get() = "Non si riesce a correggere da solo: va sistemato a mano."
    open fun correggiTutti(n: Int): String = "Correggi tutti ($n)"
    open fun corretti(n: Int): String =
        if (n == 1) "Corretto un problema: si annulla con ↶." else "Corretti $n problemi: un solo ↶ li annulla tutti."
    open fun corretti(n: Int, fatto: String): String =
        if (n == 1) "Corretto un problema ($fatto): si annulla con ↶." else "Corretti $n problemi ($fatto): un solo ↶ li annulla tutti."

    /** Quante cose di un tipo ha cambiato «Correggi» ([FixSummary]): «1 porta», «3 prese». */
    open fun quante(cosa: FixSummary.Thing, n: Int): String = "$n " + when (cosa) {
        FixSummary.Thing.Door -> if (n == 1) "porta" else "porte"
        FixSummary.Thing.Window -> if (n == 1) "finestra" else "finestre"
        FixSummary.Thing.BalconyDoor -> if (n == 1) "porta-finestra" else "porte-finestre"
        FixSummary.Thing.Passage -> if (n == 1) "passaggio" else "passaggi"
        FixSummary.Thing.Niche -> if (n == 1) "nicchia" else "nicchie"
        FixSummary.Thing.Radiator -> if (n == 1) "calorifero" else "caloriferi"
        FixSummary.Thing.WaterPoint -> if (n == 1) "punto acqua" else "punti acqua"
        FixSummary.Thing.Outlet -> if (n == 1) "presa" else "prese"
        FixSummary.Thing.Switch -> if (n == 1) "interruttore" else "interruttori"
        FixSummary.Thing.Light -> if (n == 1) "luce" else "luci"
        FixSummary.Thing.Furniture -> if (n == 1) "mobile" else "mobili"
        FixSummary.Thing.Room -> if (n == 1) "stanza" else "stanze"
    }

    /** Più cose in fila: «a, b e c». */
    open fun elenco(parti: List<String>): String =
        if (parti.size < 2) parti.joinToString() else parti.dropLast(1).joinToString(", ") + " e " + parti.last()

    /** Cosa ha fatto «Correggi», a gruppi (null: niente di quel gruppo): «aggiunto: 2 porte e 3 prese; tolto: 1 porta». */
    open fun fatto(aggiunto: String?, tolto: String?, spostato: String?, cambiato: String?): String =
        listOfNotNull(aggiunto?.let { "aggiunto: $it" }, tolto?.let { "tolto: $it" }, spostato?.let { "spostato: $it" }, cambiato?.let { "cambiato: $it" })
            .joinToString("; ")
    open val togliDallaPianta: String get() = "Togli dalla pianta"
    open val rimettiSullaPianta: String get() = "Rimetti sulla pianta"
    /** Per tutto un gruppo dell'elenco. */
    open val togliTutti: String get() = "Togli tutti dalla pianta"
    open val rimettiTutti: String get() = "Rimetti tutti"
    /** Dopo il testo di un problema il cui «!» è stato tolto dalla pianta. */
    open val toltoDallaPianta: String get() = "tolto dalla pianta"
    /** Per chi usa il lettore dello schermo: il «!» sulla pianta. */
    open fun problemiQui(n: Int): String = if (n == 1) "Un problema qui" else "$n problemi qui"
    open val condividi: String get() = "Condividi"
    open fun oggetto(progetto: String?): String = "Controllo della pianta" + (progetto?.let { " · $it" } ?: "")
    open fun muriIncrociati(a: String): String = "$a: i muri si incrociano e il contorno si taglia da solo, così area e 3D vengono sbagliati. Sposta gli angoli."
    open fun sovrapposte(a: String, b: String, m2: String): String = "$a e $b si sovrappongono per $m2 m²: quella parte conta due volte. Sposta un muro o un angolo."
    open fun fessura(a: String, b: String, cm: String): String =
        "$a e $b sono quasi attaccate: tra i muri resta una fessura di $cm cm. Di solito il muro è in comune: trascina questo muro sopra quello dell'altra stanza."
    open fun accavallati(a: String, b: String, cm: String): String =
        "$a e $b hanno due muri che si accavallano per $cm cm invece di un muro in comune: trascina questo muro sopra quello dell'altra stanza."
    open fun aperturaDoppia(cosa: String, stanze: String): String = "Due aperture nello stesso punto ($cosa, $stanze): forse messa due volte. Togline una."
    open fun muroZero(a: String, unisci: String): String = "$a: due angoli uno sopra l'altro (un muro lungo 0 cm). Con il muro selezionato, «$unisci» lo toglie."
    open fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa di $cm cm su un muro di $muro cm, non ci sta."
    open fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa arriva a $cm cm da terra, ma lì il muro è alto $muro cm."
    open fun suMuroTolto(a: String, cosa: String): String = "$a: $cosa su un muro tolto (lato aperto), che non c'è."
    open fun senzaPorta(a: String): String = "$a non ha porte né passaggi: da dove si entra?"
    open val nessunaPorta: String get() = "Sul piano non c'è nessuna porta: aggiungi porte e passaggi tra le stanze."
    open val senzaIngresso: String get() = "Nessuna porta dà fuori casa: manca la porta d'ingresso?"
    open fun finestraInterna(a: String, b: String): String = "$a: la finestra dà su $b, non fuori. È voluta?"
    open fun piccolissima(a: String, m2: String): String = "$a è piccolissima ($m2 m²): è voluta o è un pezzo rimasto?"
    open val scalaSenzaPiano: String get() = "La scala sale, ma sopra non c'è un piano: aggiungi il piano di sopra o togli la scala."
    open fun scalaFuori(piano: String): String = "La scala arriva fuori dalle stanze del piano di sopra ($piano)."
    open fun senzaCalorifero(a: String): String = "$a: nessun calorifero, mentre altre stanze ce l'hanno. Se si scalda in un altro modo va bene così."
    open fun senzaAcqua(a: String): String = "$a: nessun punto acqua, mentre altre stanze ce l'hanno."
    open fun scalaRipida(alzata: String, pedata: String?): String =
        "Scala ripida: alzate di $alzata cm" + (pedata?.let { " e pedate di $it cm" } ?: "") +
            ". Per salire comodi l'alzata di solito è al massimo 18-20 cm e due alzate più una pedata fanno 62-64 cm: aggiungi alzate o scegli una pendenza più dolce (Info della scala → Gradini)."
    open fun scalaStretta(larga: String): String =
        "Scala larga $larga cm: dentro casa di solito ne servono almeno 80 (lo dice il regolamento edilizio del comune)."
    open fun scalaTestaBassa(h: String): String =
        "Sopra la scala restano $h cm fino al solaio del piano di sopra: si batte la testa (ne servono almeno 200). Lascia scoperti più gradini (Info della scala → Piano sopra)."
    open fun anteSiUrtano(stanze: String, cosa: String, cosa2: String): String =
        "$stanze: due ante si urtano aprendosi ($cosa e $cosa2). Cambia il lato del cardine o il verso di apertura."
    open fun mobileNelMuro(cosa: String, cm: String): String = "$cosa entra di $cm cm in un muro: spostalo o rimpiccioliscilo."
    open fun mobileAttraversaMuro(cosa: String): String = "$cosa attraversa un muro: spostalo dentro la stanza o rimpiccioliscilo."
    open fun antaUrta(a: String, cosa: String, cosa2: String): String =
        "$a: aprendosi, l'anta urta $cosa2 ($cosa). Sposta $cosa2 o cambia il lato del cardine o il verso di apertura."
    open fun antaUrtaMuro(a: String, cosa: String): String =
        "$a: l'anta non si apre del tutto, urta un muro o un pilastro ($cosa). Sposta l'apertura o cambia il lato del cardine o il verso di apertura."
    open fun pocaLuce(a: String, fin: String, pav: String, frazione: String, serve: String): String =
        "$a: finestre apribili di $fin m² per $pav m² di pavimento ($frazione). Per una stanza da abitare serve almeno 1/8, cioè $serve m²."
    open fun senzaFinestre(a: String, serve: String): String =
        "$a non ha finestre apribili che danno fuori: una stanza da abitare ha bisogno di luce e aria dirette, con almeno $serve m² di finestre (1/8 del pavimento)."
    open val nessunaFinestra: String get() = "Sul piano non c'è nessuna finestra: aggiungile, poi qui si controllano luce e aria delle stanze."
    open fun soffittoBasso(a: String, h: String): String = "$a: soffitto a $h m; nelle stanze da abitare deve essere almeno a 2,70 m."
    open fun soffittoBassoServizi(a: String, h: String): String = "$a: soffitto a $h m; in bagni e corridoi deve essere almeno a 2,40 m."
    open fun mansardaBassa(a: String, h: String): String =
        "$a: soffitto in pendenza alto in media $h m. Per abitarci le leggi regionali sui sottotetti di solito chiedono una media di almeno 2,40 m (fuori dai sottotetti 2,70 m)."
    open fun cameraPiccola(a: String, m2: String): String = "$a: $m2 m²; una camera da letto deve averne almeno 9 (14 se è per due persone)."
    open fun cameraPerDuePiccola(a: String, m2: String): String = "$a: $m2 m² con i letti per due persone; una camera per due deve averne almeno 14."
    open fun soggiornoPiccolo(a: String, m2: String): String = "$a: $m2 m²; il soggiorno deve averne almeno 14."
    open fun bagnoSenzaFinestra(a: String): String =
        "$a: nessuna finestra apribile verso fuori; il bagno ha bisogno dell'aspirazione meccanica (e niente apparecchi a fiamma libera)."
    open fun bagnoSuCucina(bagno: String, stanza: String): String =
        "$bagno si apre direttamente su $stanza: molti regolamenti d'igiene dei comuni vogliono un antibagno o un disimpegno tra il bagno e la cucina o il soggiorno."
    open fun bagnoIncompleto(a: String, manca: String): String =
        "$a: senza $manca. Almeno un bagno della casa deve avere vaso, bidè, vasca o doccia e lavabo."
    open val vaso: String get() = "vaso"
    open val bide: String get() = "bidè"
    open val vascaODoccia: String get() = "vasca o doccia"
    open val lavabo: String get() = "lavabo"
    open fun casaPiccola(m2: String, persone: Int, min: String): String =
        "La casa ha $m2 m² per $persone persone (contate dai letti); ne servono almeno $min (14 m² a testa per le prime 4, 10 per le altre)."
    open fun monolocalePiccolo(m2: String, min: String, perDue: Boolean): String =
        "Monolocale di $m2 m²: ne servono almeno $min per ${if (perDue) "due persone" else "una persona"}, bagno compreso."
    open fun impiantoScarso(a: String, prese: String, luci: String, minPrese: String, minLuci: String): String =
        "$a: $prese e $luci. Per l'impianto elettrico di base (CEI 64-8, livello 1) servono almeno $minPrese e $minLuci."
    open fun puntiPresa(n: Int): String = when (n) {
        0 -> "nessun punto presa"
        1 -> "1 punto presa"
        else -> "$n punti presa"
    }
    open fun puntiLuce(n: Int): String = when (n) {
        0 -> "nessun punto luce"
        1 -> "1 punto luce"
        else -> "$n punti luce"
    }
}
