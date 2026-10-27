package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DEn : TestiVista3D() {
    override val luciAccese: String get() = "💡 Lights on"
    override val luciSpente: String get() = "💡 Lights off"
    override val luciAuto: String get() = "💡 Lights: auto"
    override fun luminositaLuci(pct: Int): String = "💡 Light brightness: $pct%"
    override val vistaDallAlto: String get() = "🏠 Top view"
    override val cammina: String get() = "🚶 Walk"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Time of day: $h:$p2"
    override val soleSegueBussola: String get() =
        "The sun follows the city, the day and the north chosen with the 🧭 compass on the plan; light comes in through the windows. " +
            "When it gets dark, the house lights come on."
    override val nordCitta: String get() = "🧭 North and city"
    override val bussola: String get() = "Compass: drag it to turn the N towards true north, tap it to choose north and city"
    override val spiegazioneNord: String get() =
        "Turn the arrow until the N points to true north relative to your plan. The plan stays still: only the sun moves in 3D. It is optional: without it, north is at the top and the city is Rome."
    override val nordGradi: String get() = "North (degrees clockwise from the top)"
    override val nordInAlto: String get() = "North at the top"
    override val citta: String get() = "City"
    override val cercaCitta: String get() = "Search for the city"
    override val nessunaCitta: String get() = "None (Rome is used)"
    override fun cittaScelta(nome: Any?): String = "City: $nome"
    override val giorno: String get() = "Day"
    override val oggi: String get() = "Today"
    override val marzo21: String get() = "21 March"
    override val giugno21: String get() = "21 June"
    override val settembre21: String get() = "21 September"
    override val dicembre21: String get() = "21 December"
}
