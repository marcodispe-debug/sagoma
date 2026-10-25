package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoEn : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info
    private val ok get() = e.hoCapito

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "L shape"
        Lesson.Elements -> "Doors, windows and more"
        Lesson.OverPlan -> "On top of your floor plan"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Guide · Lesson $n of $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Step $n of $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Welcome to Sagoma!"
        CourseStep.ElementsDone -> "Every element, from «$infoTab»"
        CourseStep.Finished -> "You finished the guide!"
        CourseStep.Snap -> "Snapping 🧲"
        else -> "Done!"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "I'll guide you step by step to draw a small practice flat, in 7 short lessons. You do the actions: at every step an orange " +
                "frame shows you what to click. It's a project just for practice: your own projects are not touched."
        CourseStep.SquareDone -> "The 3.30 × 3.30 m square study is on the plan, with the measurements written on the walls. In the next lesson you add a rectangular room."
        CourseStep.RectDone -> "Here is the 4.00 × 3.30 m bedroom, next to the study. In the next lesson: an L-shaped living room."
        CourseStep.LDone -> "Here is the L-shaped living room: 6.40 × 2.70 m, with the 3.00 × 1.90 m part sticking out. In the next lesson: a room that is out of square, from tape measurements."
        CourseStep.SurveyDone -> "Here is the bathroom with its true shape, a little out of square as in reality. In the next lesson you draw a room wall by wall."
        CourseStep.WallsDone -> "Here is the hall, with the shape you drew wall by wall. You have seen the ways to create a room. In the next lesson: doors, windows, a balcony, fixtures and stairs."
        CourseStep.ElementsDone ->
            "In the «$infoTab» panel you change sizes, type and everything else of the selected element; it closes with «▼». For every element: " +
                "you pick it from «${e.elementiArchitettonici}», you place it on the plan, and the rest from «$infoTab». Last lesson: building the " +
                "rooms on top of your floor plan. It needs a new project: I'll prepare it."
        CourseStep.Snap ->
            "The «${T.schedaStanza.si}» button, down here, turns snapping on and off. When it's on, a room you drag close to another one " +
                "attaches by itself, corner to corner or wall to wall, and the wall becomes shared. Walls and corners you drag also snap to " +
                "nearby walls and to straight directions. When it's off («${T.schedaStanza.no}»), everything stays exactly where you let go. " +
                "Leave it on: now you need it to attach the living room to the bedroom."
        CourseStep.Finished ->
            "Here are the bedroom, the bathroom and the living room on top of the plan, with the right measurements. Do the same with the " +
                "other rooms: a shape to start, attached to the rooms next to it, then walls and corners in place and the exact measurements " +
                "in «$infoTab». Now you can draw your own home! You can take the guide again any time from «${e.guide}»."
        else -> ""
    }

    override val avanti: String get() = "Next"
    override val apriProgettoNuovo: String get() = "Open the new project"
    override val bastaCosi: String get() = "That's enough"
    override val vaiAiProgetti: String get() = "Go to your projects"
    override val nienteDaIndicare: String get() = "Do the action of this step, or click «${e.saltaPasso}»."
    override val tornaIndietro: String get() = "This is not the shape for this step: click «${T.comuni.indietro}» and choose the right one."
    override val vaiAllaLezione: String get() = "Or go to lesson:"
    override val voceMenu: String get() = "Interactive guide (7 lessons)"
    override val progettoProva: String get() = "Guide practice"
    override val progettoProvaPlanimetria: String get() = "Guide practice · floor plan"
    override val usaPiantina: String get() = "🧭 Use the guide's floor plan"
    override val ingresso: String get() = "Hall"
    override val notaMisure: String get() = "measurements in cm, on the inner face of the walls"

    override val apriElementi: String get() = "Click «${e.elementiArchitettonici}», down here."
    override val apriElementiAltraStanza: String get() = "To add another room click «${e.elementiArchitettonici}», down here."
    override val poiStanza: String get() = "Click «${e.stanza}»: the window with the shapes comes back."
    override fun tipo(nome: String): String = "Choose the type: «$nome»."
    override val creaStanza: String get() = "Click «${cr.creaStanza}»."

    override val scegliQuadrato: String get() =
        "When you open a new project, Sagoma asks you right away what shape the first room has. Click «${cr.quadrato}»: it's the shape for a room with four equal sides."
    override val tipoStudio: String get() = "In «${cr.tipoStanza}» click «${RoomType.Studio.label}»: Sagoma gives the room the right name and colour."
    override fun scriviLato(v: Int): String = "In the «${cr.lato2}» field type $v. It's the inside measurement, from wall to wall: the one you take with a tape."
    override val soffittoECrea: String get() = "The ceiling height is already filled in (270 cm): change it only if your room is different. Now click «${cr.creaStanza}»."

    override val scegliRettangolo: String get() = "Click «${cr.rettangolo}»: two long sides and two short ones."
    override fun scriviLarghezza(v: Int): String = "«${T.comuni.larghezza}» is the horizontal side of the room, from left to right on the plan: type $v."
    override fun scriviProfondita(v: Int): String = "«${T.comuni.profondita}» is the other side, from top to bottom: type $v."

    override val scegliL: String get() = "Click «${T.comuni.formaL}»: many living rooms and kitchens are like this, with a corner that goes in."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma asks on which side the extra part of the L sticks out. Look at the room from above, like on a floor plan: here it's «$lato»."
    override fun larghezzaPrincipale(v: Int): String = "The L is made of two parts. The main part is the larger rectangle: in «${cr.larghezzaPrincipale}» type $v."
    override fun profonditaPrincipale(v: Int): String = "In «${cr.profonditaPrincipale}» type $v."
    override fun larghezzaProtuberanza(v: Int): String = "The other part is the one sticking out. In «${cr.larghezzaProtuberanza}» type $v: it must be less than the main width."
    override fun profonditaProtuberanza(v: Int): String = "In «${cr.profonditaProtuberanza}» type how far it sticks out: $v."

    override val scegliRilievo: String get() = "Click «${cr.rilievo}»: it's for corners that are not exactly 90°, or rooms with 5 sides or more."
    override val tipoBagnoQuattroLati: String get() = "Choose the type «${RoomType.Bagno.label}». «${cr.numeroLati}» already says 4, like the sides of our bathroom."
    override fun primoLatoRilievo(v: Int): String =
        "Call one corner of the room A, then B, C and D going clockwise. Measure the sides on the wall, inside. In «${cr.lato('A', 'B')}» type $v."
    override fun lato(nome: String, v: Int): String = "In «${cr.lato(nome[0], nome[1])}» type $v."
    override fun diagonaleAC(v: Int): String =
        "Then one diagonal: from A to the opposite corner C (AC) or from B to D (BD), the one you measured. Here it's AC, already chosen: type $v. " +
            "This measurement tells Sagoma how crooked the corners are."
    override val anteprimaECrea: String get() = "Further down there is the preview of the true shape. Click «${cr.creaStanza}»."

    override val scegliMuroPerMuro: String get() = "Click «${cr.muroMuro}»: you draw the room one wall at a time, with inside measurements, like in an architect's program."
    override val primoAngolo: String get() = "Click on the plan where the first wall starts, for example here: it's the first corner of the room, inside."
    override fun verso(n: Int, a: WallArrow): String = "Wall $n: choose the direction, «${freccia(a)}»."
    override fun lunghezzaMuro(v: Int): String = "In «${T.muroPerMuro.lunghezzaCm}» type $v."
    override val aggiungi: String get() = "Click «${T.muroPerMuro.aggiungi}»."
    override fun muriFatti(n: Int, tot: Int): String = "Walls done: $n of $tot"
    override val chiudiStanza: String get() = "Only the last wall is missing, the one back to the first corner: click «${T.muroPerMuro.chiudiStanza}» and Sagoma draws it for you."
    override fun tipoIngresso(nome: String): String = "Choose the type: for the hall «$nome» is fine. Ceiling and wall thickness are already filled in."

    override val portaElementi: String get() = "Doors, windows and everything else are added from «${e.elementiArchitettonici}». Click it."
    override val porte: String get() = "Click «${e.porte}»."
    override val portaUnaAnta: String get() = "Choose the type, for example «${e.portaAd1Anta}»."
    override val cliccaMuroPorta: String get() = "Now click on the wall, where the door goes: for example here."
    override val finestraElementi: String get() = "A window goes in the same way: «${e.elementiArchitettonici}»…"
    override val finestre: String get() = "…«${e.finestre}»…"
    override val finestraDueAnte: String get() = "…«${e.finestra2Ante}»."
    override val cliccaMuroFinestra: String get() = "Click on an outside wall, where the window goes: for example here."
    override val balconeElementi: String get() = "Now a balcony: «${e.elementiArchitettonici}»…"
    override val balconiTerrazze: String get() = "…«${e.balconiTerrazze}»…"
    override val balcone: String get() = "…«${RoomType.Balcone.label}»."
    override val balconeRettangolo: String get() = "You choose the shape as for rooms: the balcony is a «${cr.rettangolo}»."
    override fun balconeLarghezza(v: Int): String = "Width $v…"
    override fun balconeProfondita(v: Int): String = "…and depth $v."
    override val balconeCrea: String get() = "Click «${cr.crea}»: the balcony is placed against the house (you can move it later by dragging its name)."
    override val portaFinestraElementi: String get() = "To step out onto the balcony you need a French window: «${e.elementiArchitettonici}»…"
    override val porteFinestre: String get() = "…«${e.porteFinestre}»…"
    override val balconeDueAnte: String get() = "…«${e.balcone2Ante}»."
    override val cliccaMuroBalcone: String get() = "Click on the wall of the house where the balcony rests, here."
    override val impiantiElementi: String get() = "Now the fixtures: radiators, sockets, switches, water points and lights. «${e.elementiArchitettonici}»…"
    override val impianti: String get() = "…«${e.impianti}»…"
    override val calorifero: String get() = "…«${e.calorifero}»."
    override val cliccaMuroCalorifero: String get() = "Radiators, sockets, switches and water points go on a wall: click it, for example here."
    override val luciElementi: String get() = "Lights instead go inside the room. «${e.elementiArchitettonici}»…"
    override val luci: String get() = "…«${e.luci}»…"
    override val lampadario: String get() = "…for example «${FixtureKind.Chandelier.label}»."
    override val cliccaStanzaLuce: String get() = "Click inside the room, where the light goes: for example here."
    override val cliccaStudio: String get() = "Last, the stairs. First click inside the room where they go, for example the study, here."
    override val scalaElementi: String get() = "Then «${e.elementiArchitettonici}»…"
    override val scale: String get() = "…«${e.scale}»…"
    override val chiocciola: String get() = "…and choose the shape, for example «${StairKind.Spiral.label}»."
    override val info: String get() =
        "The stairs are selected. For every other change click «$infoTab»: it works for every element, doors, windows, balconies, fixtures and stairs."

    override fun miniGuida(t: Tip): String = when (t) {
        Tip.Opening -> "The first time you add a door its mini guide opens: how to slide it along the wall, and sizes and swing from «$infoTab». Read it and click «$ok»."
        Tip.Outdoor -> "The balcony has its mini guide too: instead of walls there is a parapet, and in «$infoTab» you choose railing, low wall or glass. Click «$ok»."
        Tip.Fixture -> "Fixtures mini guide: the wall ones slide along the wall, and in «$infoTab» you set height from the floor and size. Click «$ok»."
        Tip.Stair -> "When you add stairs their mini guide opens and explains everything: how to move them and push them against a wall, and what you change in «$infoTab». Read it and click «$ok»."
        else -> "A mini guide opened: read it and click «$ok»."
    }

    override val ricalca: String get() =
        "Do you already have a floor plan, from the land registry, the project or just a photo? When choosing the first room of a project click «${cr.ricalcaPiantina}»."
    override val usaPiantinaGuida: String get() =
        "Usually here you click «${T.sfondo.fotoImmagine}» (or «${T.sfondo.pdf}») and choose the file of your floor plan. To try it, use the guide's plan: click «$usaPiantina»."
    override fun taraturaA(cm: Int): String =
        "The plan is under the drawing, but Sagoma doesn't know its size yet: it has to be scaled with a measurement written on it. " +
            "Let's use the top wall of the living room, $cm cm: click here, on the inner top-left corner of the living room."
    override val taraturaB: String get() = "Now click where the measurement ends: the inner top-right corner of the living room, here."
    override fun misuraReale(cm: Int): String = "Type the real distance between the two points, the one written on the plan: $cm."
    override val tara: String get() = "Click «${T.sfondo.tara2}»: now the plan is to scale, every centimetre is right."
    override val chiudiPannello: String get() =
        "Here on the right is the «${e.piantinaDisegno}» panel: it makes the plan lighter or darker, or hides it. For now close it with «${e.chiudi}»."
    override val esciMuroPerMuro: String get() =
        "«${cr.muroMuro}» started on top of the plan: you could click the corners of the rooms one by one. Here instead we use the shapes and " +
            "adjust them on the plan: click «${T.comuni.esci}»."
    override val cameraDaQuadrato: String get() = "Let's start with the bedroom, which is a rectangle: we start from a square, to see how it stretches. Click «${cr.quadrato}»."
    override fun latoCortoCamera(v: Int): String = "Type the short side of the bedroom, read on the plan: $v."
    override val spostaCamera: String get() =
        "The bedroom is there, but not in its place yet. To move a room press on its name, hold and drag it: let go when the top-left " +
            "corner is on that of the drawn bedroom (the dashed outline)."
    override fun allungaCamera(v: Int): String =
        "The bedroom on the plan is wider: $v. Just move the right wall: press on the wall, hold and drag it to the mark. While you move it the measurement changes: leave it at $v."
    override val bagnoElementi: String get() = "Now the bathroom, which is crooked: here too we start from a square. «${e.elementiArchitettonici}»…"
    override val bagnoQuadrato: String get() = "…«${cr.quadrato}»…"
    override fun latoBagno(v: Int): String = "…side $v, the top wall of the bathroom…"
    override val spostaBagno: String get() = "Drag the bathroom by its name onto the bathroom of the plan, with the top-left corner in its place."
    override val angoloC: String get() =
        "The bathroom corners are not at 90°. Press on the blue dot of the bottom-right corner and drag it to the mark, where it matches the plan."
    override val angoloD: String get() = "Then the bottom-left corner: just a little lower. The wall measurements update while you move it."

    override val soggiornoElementi: String get() = "Last room: the living room, which is L-shaped. «${e.elementiArchitettonici}»…"
    override val soggiornoL: String get() = "…«${T.comuni.formaL}»."
    override val soggiornoMisureDopo: String get() =
        "Choose the type «${RoomType.Soggiorno.label}». Leave the measurements as they are: you'll size the living room later, on top of the plan."
    override val attaccaSoggiorno: String get() =
        "On the plan the living room is attached to the bedroom, on its right. Drag the living room by its name to the dashed outline: " +
            "when its top-right corner gets close to the bedroom's, it snaps and attaches by itself."
    override fun muroSullaPiantina(muro: Int): String =
        "Now bring the walls onto the plan. Press on the ${nomeMuroSoggiorno(muro)} wall of the living room and drag it to the mark, on the " +
            "drawn wall. Roughly is enough: you'll type the exact measurement later."
    override val selezionaSoggiorno: String get() = "Click the living room's name to select it."
    override val misureInInfo: String get() =
        "When you move walls by hand the measurements never come out exactly like the plan's: look at the ones written on the living " +
            "room's walls. The right ones are typed in «$infoTab»: click it."
    override val schedaMisure: String get() = "Open the «${T.schedaStanza.misure}» tab."
    override fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "Under «${T.schedaStanza.muri}» there is the inner length of every wall. " else "") +
            "In «${T.schedaStanza.muro(numero)}», the ${nomeMuroSoggiorno(muro)} wall, type $v and press Enter."
    override fun nomeMuroSoggiorno(muro: Int): String = when (muro) {
        0 -> "top"
        1 -> "right"
        2 -> "bottom-right"
        3 -> "short middle"
        4 -> "bottom-left"
        else -> "left"
    }
}
