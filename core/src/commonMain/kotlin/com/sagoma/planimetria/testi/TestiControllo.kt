package com.sagoma.planimetria.testi

/** «Controlla la pianta»: errori di disegno, cose da controllare e norme per la casa. I nomi delle stanze arrivano già passati da [nome]. */
open class TestiControllo {
    open val voceMenu: String get() = "🔍 Controlla la pianta…"
    open val titolo: String get() = "Controlla la pianta"
    open val nessunProblema: String get() = "Nessun problema trovato."
    open val cosaControlla: String get() =
        "Cerca stanze sovrapposte o quasi attaccate, muri che si incrociano, porte e finestre doppie, più larghe del muro o più alte, " +
            "stanze senza porta, la porta d'ingresso, le scale e i minimi di legge delle stanze (finestre, altezze, metri quadri)."
    open val spiega: String get() = "Possono sbagliare metri quadri, computo e 3D. «Mostra» porta lì e lo seleziona."
    open val daCorreggere: String get() = "Da correggere"
    open val daControllare: String get() = "Da controllare"
    open val daNorme: String get() = "Norme per la casa (Italia)"
    open val spiegaNorme: String get() =
        "Minimi del DM 5 luglio 1975 per le stanze da abitare (camere, soggiorno, cucina, studio); il regolamento edilizio del comune " +
            "può chiedere di più. Le finestre contano se si aprono e danno fuori, misurate sul vano del muro."
    /** Nome di una stanza dentro un testo. */
    open fun nome(n: String): String = "«$n»"
    open val mostra: String get() = "Mostra"
    open val chiudi: String get() = "Chiudi"
    open fun troppi(n: Int): String = "Solo i primi $n: correggi questi e controlla di nuovo."
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
    open fun pocaLuce(a: String, fin: String, pav: String, frazione: String, serve: String): String =
        "$a: finestre apribili di $fin m² per $pav m² di pavimento ($frazione). Per una stanza da abitare serve almeno 1/8, cioè $serve m²."
    open fun senzaFinestre(a: String, serve: String): String =
        "$a non ha finestre apribili che danno fuori: una stanza da abitare ha bisogno di luce e aria dirette, con almeno $serve m² di finestre (1/8 del pavimento)."
    open val nessunaFinestra: String get() = "Sul piano non c'è nessuna finestra: aggiungile, poi qui si controllano luce e aria delle stanze."
    open fun soffittoBasso(a: String, h: String): String = "$a: soffitto a $h m; nelle stanze da abitare deve essere almeno a 2,70 m."
    open fun soffittoBassoServizi(a: String, h: String): String = "$a: soffitto a $h m; in bagni e corridoi deve essere almeno a 2,40 m."
    open fun cameraPiccola(a: String, m2: String): String = "$a: $m2 m²; una camera da letto deve averne almeno 9 (14 se è per due persone)."
    open fun soggiornoPiccolo(a: String, m2: String): String = "$a: $m2 m²; il soggiorno deve averne almeno 14."
}
