package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiVista3D

object TestiVista3DEn : TestiVista3D() {
    override val luciAccese: String get() = "💡 Lights on"
    override val luciSpente: String get() = "💡 Lights off"
    override val luciAuto: String get() = "💡 Lights: auto"
    override val vistaDallAlto: String get() = "🏠 Top view"
    override val cammina: String get() = "🚶 Walk"
    override fun oraGiorno(h: Any?, p2: Any?): String = "Time of day: $h:$p2"
    override val soleSorgeEstDestra: String get() =
        "The sun rises in the east (to the right of the plan) and sets in the west; light comes in through the windows. " +
            "When it gets dark, the house lights come on."
}
