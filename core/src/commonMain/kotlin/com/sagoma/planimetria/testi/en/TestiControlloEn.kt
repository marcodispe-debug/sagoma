package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloEn : TestiControllo() {
    override val voceMenu: String get() = "🔍 Check the plan…"
    override val titolo: String get() = "Check the plan"
    override val nessunProblema: String get() = "No problems found."
    override val cosaControlla: String get() =
        "Looks for overlapping or almost touching rooms, crossing walls, doubled doors and windows, or ones wider or taller than the wall, " +
            "rooms without a door, the front door and the stairs."
    override val spiega: String get() = "They can spoil areas, quantities and 3D. «Show» takes you there and selects it."
    override val daCorreggere: String get() = "To fix"
    override val daControllare: String get() = "To check"
    override val mostra: String get() = "Show"
    override val chiudi: String get() = "Close"
    override fun troppi(n: Int): String = "Only the first $n: fix these and check again."
    override fun muriIncrociati(a: String): String = "$a: the walls cross and the outline cuts itself, so area and 3D come out wrong. Move the corners."
    override fun sovrapposte(a: String, b: String, m2: String): String = "$a and $b overlap by $m2 m²: that part counts twice. Move a wall or a corner."
    override fun fessura(a: String, b: String, cm: String): String =
        "$a and $b almost touch: a $cm cm gap is left between the walls. Usually the wall is shared: drag this wall onto the other room's wall."
    override fun accavallati(a: String, b: String, cm: String): String =
        "$a and $b have two walls overlapping by $cm cm instead of one shared wall: drag this wall onto the other room's wall."
    override fun aperturaDoppia(cosa: String, stanze: String): String = "Two openings in the same spot ($cosa, $stanze): maybe placed twice. Remove one."
    override fun muroZero(a: String, unisci: String): String = "$a: two corners on top of each other (a 0 cm wall). With the wall selected, «$unisci» removes it."
    override fun troppoLarga(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa of $cm cm on a $muro cm wall, it doesn't fit."
    override fun sopraIlMuro(a: String, cosa: String, cm: String, muro: String): String = "$a: $cosa reaches $cm cm from the floor, but the wall there is $muro cm high."
    override fun suMuroTolto(a: String, cosa: String): String = "$a: $cosa on a removed wall (open side), which isn't there."
    override fun senzaPorta(a: String): String = "$a has no doors or openings: how do you get in?"
    override val nessunaPorta: String get() = "There are no doors on this floor: add doors and openings between the rooms."
    override val senzaIngresso: String get() = "No door leads outside: is the front door missing?"
    override fun finestraInterna(a: String, b: String): String = "$a: the window looks onto $b, not outside. Is that intended?"
    override fun piccolissima(a: String, m2: String): String = "$a is tiny ($m2 m²): is it intended or a leftover piece?"
    override val scalaSenzaPiano: String get() = "The stairs go up, but there is no floor above: add the floor above or remove the stairs."
    override fun scalaFuori(piano: String): String = "The stairs arrive outside the rooms of the floor above ($piano)."
}
