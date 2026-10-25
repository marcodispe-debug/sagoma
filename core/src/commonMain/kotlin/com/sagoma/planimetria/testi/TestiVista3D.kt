package com.sagoma.planimetria.testi

/** Vista 3D: luci, camminata, ora del giorno. */
open class TestiVista3D {
    open val luciAccese: String get() = "💡 Luci accese"
    open val luciSpente: String get() = "💡 Luci spente"
    open val luciAuto: String get() = "💡 Luci: auto"
    open val vistaDallAlto: String get() = "🏠 Vista dall'alto"
    open val cammina: String get() = "🚶 Cammina"
    open fun oraGiorno(h: Any?, p2: Any?): String = "Ora del giorno: $h:$p2"
    open val soleSegueBussola: String get() =
        "Il sole segue la città, il giorno e il nord scelti con la bussola 🧭 sulla pianta; la luce entra dalle finestre. " +
            "Quando fa buio si accendono le luci della casa."
    open val nordCitta: String get() = "🧭 Nord e città"
    open val bussola: String get() = "Bussola: trascinala per girare la N verso il nord vero, toccala per scegliere nord e città"
    open val spiegazioneNord: String get() =
        "Gira la freccia finché la N punta dove è il nord vero rispetto alla tua pianta. La pianta resta ferma: nel 3D si sposta solo il sole. È facoltativo: senza, il nord è in alto e la città è Roma."
    open val nordGradi: String get() = "Nord (gradi in senso orario dall'alto)"
    open val nordInAlto: String get() = "Nord in alto"
    open val citta: String get() = "Città"
    open val cercaCitta: String get() = "Cerca la città"
    open val nessunaCitta: String get() = "Nessuna (vale Roma)"
    open fun cittaScelta(nome: Any?): String = "Città: $nome"
    open val giorno: String get() = "Giorno"
    open val oggi: String get() = "Oggi"
    open val marzo21: String get() = "21 marzo"
    open val giugno21: String get() = "21 giugno"
    open val settembre21: String get() = "21 settembre"
    open val dicembre21: String get() = "21 dicembre"
}
