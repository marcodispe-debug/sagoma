package com.sagoma.planimetria.testi

/** Vista 3D: luci, camminata, ora del giorno. */
open class TestiVista3D {
    open val luciAccese: String get() = "💡 Luci accese"
    open val luciSpente: String get() = "💡 Luci spente"
    open val luciAuto: String get() = "💡 Luci: auto"
    open val vistaDallAlto: String get() = "🏠 Vista dall'alto"
    open val cammina: String get() = "🚶 Cammina"
    open fun oraGiorno(h: Any?, p2: Any?): String = "Ora del giorno: $h:$p2"
    open val soleSorgeEstDestra: String get() =
        "Il sole sorge a est (a destra della pianta) e tramonta a ovest; la luce entra dalle finestre. " +
            "Quando fa buio si accendono le luci della casa."
}
