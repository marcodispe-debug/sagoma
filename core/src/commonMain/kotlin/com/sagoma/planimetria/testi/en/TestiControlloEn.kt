package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.editor.FixSummary
import com.sagoma.planimetria.testi.TestiControllo

object TestiControlloEn : TestiControllo() {
    override val voceMenu: String get() = "🔍 Check the plan…"
    override val titolo: String get() = "Check the plan"
    override val nessunProblema: String get() = "No problems found."
    override val cosaControlla: String get() =
        "Looks for overlapping or almost touching rooms, crossing walls, doubled doors and windows, or ones wider or taller than the wall, " +
            "rooms without a door, leaves that hit something when opening, the front door, the stairs, the systems and the legal minimums of the rooms (windows, heights, square metres)."
    override val spiega: String get() =
        "They can spoil areas, quantities and 3D. On the plan a «!» marks where each problem is. «Show» takes you there and selects it; " +
            "«Fix» sorts it out by itself (undo with ↶); «Remove from plan» removes its «!»."
    override val daCorreggere: String get() = "To fix"
    override val daControllare: String get() = "To check"
    override val daNorme: String get() = "Rules for homes (Italy)"
    override val spiegaNorme: String get() =
        "Minimums of the Italian decree of 5 July 1975 for living spaces (bedrooms, living room, kitchen, study) and bathrooms; the local building code " +
            "may ask for more. Windows count if they open and face outside, measured on the wall opening. Once you place sockets, also the socket " +
            "outlets and light points of a basic electrical system (CEI 64-8, level 1)."
    override fun nome(n: String): String = "«$n»"
    override val mostra: String get() = "Show"
    override val chiudi: String get() = "Close"
    override fun troppi(n: Int): String = "Only the first $n: fix these and check again."
    override val primaDiEsportareTitolo: String get() = "Before exporting"
    override fun primaDiEsportare(n: Int): String =
        "The plan has ${if (n == 1) "a drawing mistake" else "$n drawing mistakes"} (⚠): measures, areas and quantities could be wrong in the exported file too."
    override val controlla: String get() = "Check"
    override val esportaLoStesso: String get() = "Export anyway"
    override val correggi: String get() = "Fix"
    override val nonSiCorregge: String get() = "This can't be fixed automatically: it needs fixing by hand."
    override fun correggiTutti(n: Int): String = "Fix all ($n)"
    override fun corretti(n: Int): String =
        if (n == 1) "Fixed one problem: undo with ↶." else "Fixed $n problems: one ↶ undoes them all."
    override fun corretti(n: Int, fatto: String): String =
        if (n == 1) "Fixed one problem ($fatto): undo with ↶." else "Fixed $n problems ($fatto): one ↶ undoes them all."
    override fun quante(cosa: FixSummary.Thing, n: Int): String = "$n " + when (cosa) {
        FixSummary.Thing.Door -> if (n == 1) "door" else "doors"
        FixSummary.Thing.Window -> if (n == 1) "window" else "windows"
        FixSummary.Thing.BalconyDoor -> if (n == 1) "French window" else "French windows"
        FixSummary.Thing.Passage -> if (n == 1) "doorway" else "doorways"
        FixSummary.Thing.Niche -> if (n == 1) "niche" else "niches"
        FixSummary.Thing.Radiator -> if (n == 1) "radiator" else "radiators"
        FixSummary.Thing.WaterPoint -> if (n == 1) "water point" else "water points"
        FixSummary.Thing.Outlet -> if (n == 1) "socket" else "sockets"
        FixSummary.Thing.Switch -> if (n == 1) "switch" else "switches"
        FixSummary.Thing.Light -> if (n == 1) "light" else "lights"
        FixSummary.Thing.Furniture -> if (n == 1) "piece of furniture" else "pieces of furniture"
        FixSummary.Thing.Room -> if (n == 1) "room" else "rooms"
    }
    override fun elenco(parti: List<String>): String =
        if (parti.size < 2) parti.joinToString() else parti.dropLast(1).joinToString(", ") + " and " + parti.last()
    override fun fatto(aggiunto: String?, tolto: String?, spostato: String?, cambiato: String?): String =
        listOfNotNull(aggiunto?.let { "added: $it" }, tolto?.let { "removed: $it" }, spostato?.let { "moved: $it" }, cambiato?.let { "changed: $it" })
            .joinToString("; ")
    override val togliDallaPianta: String get() = "Remove from plan"
    override val rimettiSullaPianta: String get() = "Show on plan"
    override val togliTutti: String get() = "Remove all from plan"
    override val rimettiTutti: String get() = "Show all"
    override val toltoDallaPianta: String get() = "removed from the plan"
    override fun problemiQui(n: Int): String = if (n == 1) "One problem here" else "$n problems here"
    override val condividi: String get() = "Share"
    override fun oggetto(progetto: String?): String = "Plan check" + (progetto?.let { " · $it" } ?: "")
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
    override fun senzaCalorifero(a: String): String = "$a: no radiator, while other rooms have one. If it is heated another way, that's fine."
    override fun senzaAcqua(a: String): String = "$a: no water point, while other rooms have one."
    override fun scalaRipida(alzata: String, pedata: String?): String =
        "Steep stairs: risers of $alzata cm" + (pedata?.let { " and treads of $it cm" } ?: "") +
            ". To climb comfortably a riser is usually 18-20 cm at most and two risers plus a tread make 62-64 cm: add risers or choose a gentler slope (stairs Info → Steps)."
    override fun scalaStretta(larga: String): String =
        "Stairs $larga cm wide: inside a home at least 80 are usually needed (set by the local building code)."
    override fun scalaTestaBassa(h: String): String =
        "Above the stairs there are $h cm up to the slab of the floor above: you hit your head (at least 200 are needed). Leave more steps uncovered (stairs Info → Floor above)."
    override fun anteSiUrtano(stanze: String, cosa: String, cosa2: String): String =
        "$stanze: two leaves hit each other when opening ($cosa and $cosa2). Swap the hinge side or the opening direction."
    override fun mobileNelMuro(cosa: String, cm: String): String = "$cosa goes $cm cm into a wall: move it or make it smaller."
    override fun mobileAttraversaMuro(cosa: String): String = "$cosa goes through a wall: move it inside the room or make it smaller."
    override fun antaUrta(a: String, cosa: String, cosa2: String): String =
        "$a: when opening, the leaf hits $cosa2 ($cosa). Move $cosa2 or swap the hinge side or the opening direction."
    override fun antaUrtaMuro(a: String, cosa: String): String =
        "$a: the leaf can't open fully, it hits a wall or a column ($cosa). Move the opening or swap the hinge side or the opening direction."
    override fun pocaLuce(a: String, fin: String, pav: String, frazione: String, serve: String): String =
        "$a: $fin m² of opening windows for $pav m² of floor ($frazione). A living space needs at least 1/8, that is $serve m²."
    override fun senzaFinestre(a: String, serve: String): String =
        "$a has no opening windows facing outside: a living space needs direct light and air, with at least $serve m² of windows (1/8 of the floor)."
    override val nessunaFinestra: String get() = "There are no windows on this floor: add them, then light and air of the rooms are checked here."
    override fun soffittoBasso(a: String, h: String): String = "$a: ceiling at $h m; living spaces need at least 2.70 m."
    override fun soffittoBassoServizi(a: String, h: String): String = "$a: ceiling at $h m; bathrooms and corridors need at least 2.40 m."
    override fun mansardaBassa(a: String, h: String): String =
        "$a: sloping ceiling with an average height of $h m. To live in an attic, regional rules usually want an average of at least 2.40 m (2.70 m outside attics)."
    override fun cameraPiccola(a: String, m2: String): String = "$a: $m2 m²; a bedroom needs at least 9 (14 for two people)."
    override fun cameraPerDuePiccola(a: String, m2: String): String = "$a: $m2 m² with beds for two people; a bedroom for two needs at least 14."
    override fun soggiornoPiccolo(a: String, m2: String): String = "$a: $m2 m²; the living room needs at least 14."
    override fun bagnoSenzaFinestra(a: String): String =
        "$a: no opening window to the outside; the bathroom needs mechanical extraction (and no open-flame appliances)."
    override fun bagnoSuCucina(bagno: String, stanza: String): String =
        "$bagno opens straight onto $stanza: many local health codes want an anteroom or a hallway between the bathroom and the kitchen or living room."
    override fun bagnoIncompleto(a: String, manca: String): String =
        "$a: no $manca. At least one bathroom in the home needs a toilet, a bidet, a bath or shower and a washbasin."
    override val vaso: String get() = "toilet"
    override val bide: String get() = "bidet"
    override val vascaODoccia: String get() = "bath or shower"
    override val lavabo: String get() = "washbasin"
    override fun casaPiccola(m2: String, persone: Int, min: String): String =
        "The home has $m2 m² for $persone people (counted from the beds); it needs at least $min (14 m² each for the first 4, 10 for the others)."
    override fun monolocalePiccolo(m2: String, min: String, perDue: Boolean): String =
        "Studio flat of $m2 m²: it needs at least $min for ${if (perDue) "two people" else "one person"}, bathroom included."
    override fun impiantoScarso(a: String, prese: String, luci: String, minPrese: String, minLuci: String): String =
        "$a: $prese and $luci. A basic electrical system (Italian standard CEI 64-8, level 1) needs at least $minPrese and $minLuci."
    override fun puntiPresa(n: Int): String = when (n) {
        0 -> "no socket outlets"
        1 -> "1 socket outlet"
        else -> "$n socket outlets"
    }
    override fun puntiLuce(n: Int): String = when (n) {
        0 -> "no light points"
        1 -> "1 light point"
        else -> "$n light points"
    }
}
