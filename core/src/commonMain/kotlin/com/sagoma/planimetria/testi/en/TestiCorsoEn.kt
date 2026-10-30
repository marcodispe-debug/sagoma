package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.WallArrow
import com.sagoma.planimetria.model.FixtureKind
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.StairKind
import com.sagoma.planimetria.testi.T
import com.sagoma.planimetria.testi.TestiCorso

object TestiCorsoEn : TestiCorso() {
    private val infoTab get() = T.schedaStanza.info

    override fun lezione(l: Lesson): String = when (l) {
        Lesson.LShape -> "L shape"
        Lesson.Elements -> "Doors, windows and more"
        Lesson.Check -> "Check the plan"
        Lesson.Sun -> "The sun and the compass"
        Lesson.OverPlan -> "On top of your floor plan"
        else -> super.lezione(l)
    }

    override fun intestazione(n: Int, tot: Int, nome: String): String = "Guide · Lesson $n of $tot · $nome"
    override fun passo(n: Int, tot: Int): String = "Step $n of $tot"

    override fun titoloScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome -> "Welcome to Sagoma!"
        CourseStep.ElementsDone -> "Every element, from «$infoTab»"
        CourseStep.Finished -> "You finished the guide!"
        CourseStep.Snap -> "Moving rooms and the snap 🧲"
        CourseStep.InnerFace -> "The inner side of the walls"
        CourseStep.SunDone -> "Your home's sun"
        else -> "Done!"
    }

    override fun testoScheda(s: CourseStep): String = when (s) {
        CourseStep.Welcome ->
            "I'll guide you step by step to draw the home of this made-up floor plan, in ${Lesson.entries.count { it.inCourse }} short lessons: one room at a time, each " +
                "in its place as in the drawing. You do the actions: at every step an orange frame shows you what to click. It's a " +
                "project just for practice: your own projects are not touched."
        CourseStep.SquareDone ->
            "The study is ready: 3.30 × 3.30 m. You have seen all the ways to change the shape of a room: the measurements when you " +
                "create it, dragging walls and corners, the exact measurements in «$infoTab» and «${e.annulla}» to go back. In the " +
                "next lesson: the L-shaped living room, above the study."
        CourseStep.Snap ->
            "The living room was created away from the house. You move a room by dragging it by its name. The «${T.schedaStanza.si}» " +
                "snap, down here, is on: when you bring the room close to another one, it snaps and attaches by itself, corner to " +
                "corner, and the wall between the two becomes a single one. When it's off («${T.schedaStanza.no}»), the room stays " +
                "exactly where you let go. Leave it on."
        CourseStep.LDone ->
            "The living room is attached above the study, as on the plan: the wall between the two rooms is now a single shared " +
                "wall. In the next lesson: the bedroom, a rectangle to the right of the living room."
        CourseStep.RectDone -> "The bedroom is attached to the right of the living room. In the next lesson: the bathroom, below the bedroom, which is a little out of square."
        CourseStep.SurveyDone ->
            "The bathroom is below the bedroom, with its true shape: a little out of square, as in reality. In the next lesson: the " +
                "hall, drawn wall by wall in the space left between the rooms."
        CourseStep.WallsDone ->
            "The hall closes the house: now all the rooms are connected, as on the plan. You have seen all the ways to create a " +
                "room. In the next lesson: the free wall, a wall on its own that doesn't close a room."
        CourseStep.FreeWallDone ->
            "The bedroom now has its walk-in closet, divided by a partition with the way in at the end. In the «$infoTab» panel of the " +
                "free wall you also choose the height (up to the ceiling or lower, like a low wall), the colour and the curve; and you " +
                "can add doors and windows to free walls too. In the next lesson: doors, windows, a balcony, fixtures and stairs."
        CourseStep.ElementsDone ->
            "In the «$infoTab» panel you change sizes, type and everything else of the selected element; it closes with «▼». For every element: " +
                "you pick it from «${e.elementiArchitettonici}», you place it on the plan, and the rest from «$infoTab». " +
                "Next lesson: the plan check, which finds drawing mistakes by itself and fixes many of them."
        CourseStep.CheckDone ->
            "Now you know the plan check: the full list from the «⋮» menu, «✓ ${T.controllo.correggi}» and «✓ Fix all» that sort things out " +
                "by themselves («${e.annulla}» goes back), the «!» on the plan to see what is at a spot and «${T.controllo.togliDallaPianta}» for the " +
                "ones that are fine as they are (you put them back from the list). Before you export, Sagoma warns you if there are still drawing mistakes. " +
                "Last lesson: building the rooms on top of your floor plan. It needs a new project: I'll prepare it."
        CourseStep.Finished ->
            "Here are the bedroom, the bathroom and the living room on top of the plan, with the right measurements. Do the same with the " +
                "other rooms: a shape to start, attached to the rooms next to it, then walls and corners in place and the exact measurements " +
                "in «$infoTab». Now you can draw your own home! You can take the guide again any time from «${e.guide}»."
        CourseStep.SunDone ->
            "Done. North and city stay in the project and you can change them any time from the compass or the ⋮ menu; day and " +
                "time are just for looking. None of this is required: whatever you leave out stays as it was (north up, Rome, today)."
        CourseStep.InnerFace ->
            "Before moving the bedroom, one important thing. Sagoma measures rooms from the inner side of the walls, like the " +
                "measurements written on the plan: the wall thickness goes outwards. When you put a room on top of the plan, match the " +
                "inner side of the drawn walls as closely as possible with the inner side of the walls on the plan: look at the inner " +
                "line of the wall, not the middle or the outer side. That way measurements, square metres and the rooms next door come out right."
        else -> ""
    }

    override val avanti: String get() = "Next"
    override val apriProgettoNuovo: String get() = "Open the new project"
    override val bastaCosi: String get() = "That's enough"
    override val vaiAiProgetti: String get() = "Go to your projects"
    override val nienteDaIndicare: String get() = "Do the action of this step, or click «${e.saltaPasso}»."
    override val tornaIndietro: String get() = "This is not the shape for this step: click «${T.comuni.indietro}» and choose the right one."
    override val vaiAllaLezione: String get() = "Or go to lesson:"
    override val voceMenu: String get() = "Interactive guide (${Lesson.entries.count { it.inCourse }} lessons)"
    override val progettoProva: String get() = "Guide practice"
    override val progettoProvaPlanimetria: String get() = "Guide practice · floor plan"
    override val usaPiantina: String get() = "🧭 Use the guide's floor plan"
    override val ingresso: String get() = "Hall"
    override val cabinaArmadio: String get() = "walk-in closet"
    override val notaMisure: String get() = "measurements in cm, on the inner face of the walls"

    override val apriElementi: String get() = "Click «${e.elementiArchitettonici}», down here."
    override val apriElementiAltraStanza: String get() = "To add another room click «${e.elementiArchitettonici}», down here."
    override val poiStanza: String get() = "Click «${e.stanza}»: the window with the shapes comes back."
    override fun tipo(nome: String): String = "Choose the type: «$nome»."
    override val creaStanza: String get() = "Click «${cr.creaStanza}»."
    override fun selezionaStanza(nome: String): String = "Click the room's name, «$nome», to select it."
    override val accendiAgganci: String get() = "The snap is off: click «${T.schedaStanza.no}», down here, to turn it on."
    override val schedaMisure: String get() = "Open the «${T.schedaStanza.misure}» tab."
    override fun latoMuro(a: WallArrow): String = when (a) {
        WallArrow.UP -> "top"
        WallArrow.RIGHT -> "right"
        WallArrow.DOWN -> "bottom"
        WallArrow.LEFT -> "left"
    }

    override val scegliQuadrato: String get() =
        "When you open a new project, Sagoma asks you right away what shape the first room has. Let's start with the study, the " +
            "square room at the bottom left of the plan: click «${cr.quadrato}»."
    override val tipoStudio: String get() = "In «${cr.tipoStanza}» click «${RoomType.Studio.label}»: Sagoma gives the room the right name and colour."
    override fun scriviLato(v: Int): String =
        "In the «${cr.lato2}» field type $v: it's the first way to give a room its shape, with the measurements when you create it. " +
            "It's the inside measurement, from wall to wall: the one you take with a tape."
    override val soffittoECrea: String get() = "The ceiling height is already filled in (270 cm): change it only if your room is different. Now click «${cr.creaStanza}»."
    override val trascinaMuroStudio: String get() =
        "Second way: dragging walls. Press on the right wall of the study, hold and drag it to the mark: the room gets wider and the " +
            "measurement written on the wall changes while you move it."
    override fun misureEsatte(v: Int): String =
        "Dragging by hand, the measurement never comes out exact. Third way, the most precise: typing the measurements. Let's bring " +
            "the study back to $v cm: click «$infoTab»."
    override fun muroStudio(numero: Int, lato: WallArrow, v: Int): String =
        "Under «${T.schedaStanza.muri}» there is the inner length of every wall. In «${T.schedaStanza.muro(numero)}», the " +
            "${latoMuro(lato)} wall, type $v and press Enter: the next wall moves and the study is square again."
    override val trascinaAngoloStudio: String get() =
        "Fourth way, for out-of-square rooms: dragging a corner. Press on the blue dot of the bottom-right corner and drag it to the mark."
    override val annullaAngolo: String get() =
        "But the study has straight corners. When you make a mistake, «${e.annulla}», up here, removes the last change: click it."

    override val scegliRettangolo: String get() = "Click «${cr.rettangolo}»: it's the bedroom, to the right of the living room, with two long sides and two short ones."
    override val spostaCameraAccanto: String get() =
        "The bedroom is also created away from the house: drag it by its name to the dashed outline, to the right of the living " +
            "room. With the snap on it attaches by itself."
    override fun scriviLarghezza(v: Int): String = "«${T.comuni.larghezza}» is the horizontal side of the room, from left to right on the plan: type $v."
    override fun scriviProfondita(v: Int): String = "«${T.comuni.profondita}» is the other side, from top to bottom: type $v."

    override val scegliL: String get() =
        "Click «${T.comuni.formaL}»: it's the living room, which on the plan is above the study. Many living rooms and kitchens are " +
            "like this, with a corner that goes in."
    override val spostaSoggiorno: String get() =
        "Drag the living room by its name to the dashed outline, above the study: when it gets close it snaps and attaches by itself."
    override fun sporgeInBassoADestra(lato: String): String =
        "Sagoma asks on which side the extra part of the L sticks out. Look at the room from above, like on a floor plan: here it's «$lato»."
    override fun larghezzaPrincipale(v: Int): String = "The L is made of two parts. The main part is the larger rectangle: in «${cr.larghezzaPrincipale}» type $v."
    override fun profonditaPrincipale(v: Int): String = "In «${cr.profonditaPrincipale}» type $v."
    override fun larghezzaProtuberanza(v: Int): String = "The other part is the one sticking out. In «${cr.larghezzaProtuberanza}» type $v: it must be less than the main width."
    override fun profonditaProtuberanza(v: Int): String = "In «${cr.profonditaProtuberanza}» type how far it sticks out: $v."

    override val scegliRilievo: String get() =
        "Click «${cr.rilievo}»: the bathroom, below the bedroom, has corners that are not exactly 90°. It's also for rooms with 5 sides or more."
    override val spostaBagnoSotto: String get() = "Drag the bathroom by its name to the dashed outline, below the bedroom: it attaches by itself."
    override val tipoBagnoQuattroLati: String get() = "Choose the type «${RoomType.Bagno.label}». «${cr.numeroLati}» already says 4, like the sides of our bathroom."
    override fun primoLatoRilievo(v: Int): String =
        "Call one corner of the room A, then B, C and D going clockwise. Measure the sides on the wall, inside. In «${cr.lato('A', 'B')}» type $v."
    override fun lato(nome: String, v: Int): String = "In «${cr.lato(nome[0], nome[1])}» type $v."
    override fun diagonaleAC(v: Int): String =
        "Then one diagonal: from A to the opposite corner C (AC) or from B to D (BD), the one you measured. Here it's AC, already chosen: type $v. " +
            "This measurement tells Sagoma how crooked the corners are."
    override val anteprimaECrea: String get() = "Further down there is the preview of the true shape. Click «${cr.creaStanza}»."

    override val scegliMuroPerMuro: String get() =
        "Click «${cr.muroMuro}»: the hall fills the space left between the rooms, and you draw it one wall at a time, with inside " +
            "measurements, like in an architect's program."
    override val primoAngolo: String get() =
        "Click here, below the bedroom and next to the living room: it's the first corner of the hall, inside. Sagoma snaps it to " +
            "the right corner between the walls."
    override fun verso(n: Int, a: WallArrow): String = "Wall $n: choose the direction, «${freccia(a)}»."
    override fun lunghezzaMuro(v: Int): String = "In «${T.muroPerMuro.lunghezzaCm}» type $v."
    override val aggiungi: String get() = "Click «${T.muroPerMuro.aggiungi}»."
    override fun muriFatti(n: Int, tot: Int): String = "Walls done: $n of $tot"
    override val chiudiStanza: String get() = "Only the last wall is missing, the one back to the first corner: click «${T.muroPerMuro.chiudiStanza}» and Sagoma draws it for you."
    override fun tipoIngresso(nome: String): String = "Choose the type: for the hall «$nome» is fine. Ceiling and wall thickness are already filled in."

    override fun selezionaCameraTramezzo(nome: String): String =
        "A free wall is a wall on its own that doesn't close a room: for example the partition of the walk-in closet in the " +
            "bedroom, as on the plan. The wall is created in the selected room: click the bedroom's name, «$nome»."
    override val apriElementiMuroSingolo: String get() = "Now click «${e.elementiArchitettonici}», down here."
    override val scegliMuroSingolo: String get() =
        "At the bottom of the window there is «${cr.muroSingolo}»: it only appears when there is already at least one room. Click it."
    override val spostaTramezzo: String get() =
        "The wall is created straight across the bedroom, from one wall to the other, and it's already selected. You move it by " +
            "dragging: press on the wall, hold and bring it to the dashed line, where the partition goes. Both ends stay attached to the walls."
    override val selezionaMuroSingolo: String get() = "Click the free wall to select it: the dots appear at its two ends."
    override val accorciaTramezzo: String get() =
        "The partition doesn't go all the way: it leaves the way into the walk-in closet. Press on the blue dot at the bottom, at " +
            "the end of the wall, and drag it up to the mark: the wall gets shorter."
    override val infoTramezzo: String get() = "As for rooms, the exact measurements are written in «$infoTab»: click it."
    override fun lunghezzaTramezzo(v: Int): String =
        "In «${T.comuni.lunghezza}» write $v: the wall gets longer or shorter from its end, the start at the top stays where it is."
    override fun spessoreTramezzo(v: Int): String = "A plasterboard partition is thin: in «${T.comuni.spessore}» write $v."

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
    override val balconeCrea: String get() =
        "Click «${cr.crea}»: the balcony is created in its place, above the bedroom, already against the house (you can move it later by dragging its name)."
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
    override val luceSemplice: String get() = "…and here «${T.arredi.puntoLuceSemplice}»: you place it by clicking in the room."
    override val cliccaStanzaLuce: String get() = "Click inside the room, where the light goes: for example here."
    override val cliccaStudio: String get() = "Last, the stairs. First click inside the room where they go, for example the study, here."
    override val scalaElementi: String get() = "Then «${e.elementiArchitettonici}»…"
    override val scale: String get() = "…«${e.scale}»…"
    override val chiocciola: String get() = "…and choose the shape, for example «${StairKind.Spiral.label}»."
    override val info: String get() =
        "The stairs are selected. For every other change click «$infoTab»: it works for every element, doors, windows, balconies, fixtures and stairs."

    override val controlloMenu: String get() = "A helper to use often: the plan check, which looks for the drawing mistakes that usually slip through. Click the «⋮» menu up here."
    override val controlloVoce: String get() = "…and «${T.controllo.voceMenu}»."
    override val controlloRiapri: String get() = "Open the check again: the «⋮» menu up here…"
    override val controlloCorreggiTutti: String get() =
        "Here is the list: ⚠ what to fix, ? what to check, 📏 the housing rule minimums. Many problems sort themselves out: " +
            "«✓ Fix all» fixes a whole group at once (for example it adds the missing doors) and a single «${e.annulla}» undoes it. Click it."
    override val controlloCorreggi: String get() =
        "Here is the list: ⚠ what to fix, ? what to check, 📏 the housing rule minimums. Many problems sort themselves out: " +
            "click «✓ ${T.controllo.correggi}» («${e.annulla}» goes back)."
    override val controlloChiudi: String get() =
        "Done: the list updates by itself. «${T.controllo.mostra}» takes you to the right spot and selects it, «${T.controllo.condividi}» sends " +
            "the list to whoever helps you. Now click «${T.controllo.chiudi}»."
    override val controlloSegno: String get() =
        "The check also works by itself while you draw: the badge at the top left of the plan tells you how many problems there are (tap it " +
            "to open the list again) and a «!» marks the spot of each one: red for mistakes, yellow for things to check, blue for the rules. Click this «!»."
    override val controlloTogli: String get() =
        "The card tells you what is at this spot, with «✓ ${T.controllo.correggi}» (where possible) and «${T.controllo.mostra}». If it is fine " +
            "for you as it is, «${T.controllo.togliDallaPianta}» removes the «!»: the problem stays in the list and you put it back from there. Click «${T.controllo.togliDallaPianta}»."
    override val controlloSegnoDiNuovo: String get() = "Click a «!» on the plan: the card of that spot opens."
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
    override val sceltaDopoTaratura: String get() =
        "The calibration is saved: the plan is now to scale. Now choose how to draw the first room: with «${cr.muroMuro}» you click " +
            "its corners on the plan, with a shape you create it and then bring it on top of the plan. Here we use the shapes. " +
            "Let's start with the bedroom, which is a rectangle: we start from a square, to see how it stretches. Click «${cr.quadrato}»."
    override val cameraDaQuadrato: String get() = "Let's start with the bedroom, which is a rectangle: we start from a square, to see how it stretches. Click «${cr.quadrato}»."
    override fun latoCortoCamera(v: Int): String = "Type the short side of the bedroom, read on the plan: $v."
    override val spostaCamera: String get() =
        "The bedroom is there, but not in its place yet. To move a room press on its name, hold and drag it: let go when the top-left " +
            "inner corner is on the inner corner of the drawn bedroom (the dashed outline)."
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
            "with the «${T.schedaStanza.si}» snap on, when its top-right corner gets close to the bedroom's, it snaps and attaches by itself."
    override fun muroSullaPiantina(muro: Int): String =
        "Now bring the walls onto the plan. Press on the ${nomeMuroSoggiorno(muro)} wall of the living room and drag it to the mark, on the " +
            "drawn wall. Roughly is enough: you'll type the exact measurement later."
    override val misureInInfo: String get() =
        "When you move walls by hand the measurements never come out exactly like the plan's: look at the ones written on the living " +
            "room's walls. The right ones are typed in «$infoTab»: click it."
    override fun muroInfo(numero: Int, muro: Int, v: Int, first: Boolean): String =
        (if (first) "Under «${T.schedaStanza.muri}» there is the inner length of every wall. " else "") +
            "In «${T.schedaStanza.muro(numero)}», the ${nomeMuroSoggiorno(muro)} wall, type $v and press Enter."

    override val apriPannello: String get() =
        "One last thing: to see the drawing on top of the plan better, click «${e.piantinaDisegno}», here at the bottom right."
    override val trasparenzaPiantina: String get() =
        "Here you adjust the plan: dragging the slider makes it lighter or darker, the switch hides it. Below is the same for the " +
            "drawing. When you are done, close the panel with «${T.comuni.chiudi}». Try it: drag the plan slider."

    override fun intestazioneSola(nome: String): String = "Guide · $nome"
    override val giraBussola: String get() =
        "The compass 🧭 tells where north is compared to your plan: it's only used to let the sun in from the right side in 3D. " +
            "You don't have to fill in all this information: you can skip every step with «${e.saltaPasso}», or exit. If you know " +
            "where north is, press on the compass and drag it round until the N points there. The plan never turns."
    override fun nordOra(gradi: Int): String = "North: $gradi°"
    override val toccaBussola: String get() =
        "If you like, also choose the city of the house: it decides how high the sun rises and when it rises and sets. Tap the " +
            "compass, without dragging it. This is optional too: with no city Rome is used."
    override val sceltaCitta: String get() =
        "Here you can also type the degrees of north. For the city, type it in «${T.vista3D.cercaCitta}» and click it in the list. " +
            "Then click «${T.comuni.chiudi}». If you're not interested, just close it."
    override val apriVista3d: String get() = "To see the sun click «${e.vista3d}», here at the bottom right. If you don't need it, feel free to exit."
    override val tornaAllaPianta: String get() = "The compass is on the plan: click «${e.pianta2d}» to go back to it."
    override val tornaAl3d: String get() = "The sun is seen in 3D: click «${e.vista3d}»."
    override val apriOra: String get() = "The sun follows the time of day: click the time with the sun ☀, here at the bottom right. The time slider opens."
    override val riapriOra: String get() = "Open the time slider again: click the time with the sun ☀, here at the bottom right."
    override val trascinaOra: String get() =
        "Drag the slider: the sun moves with the time and the light comes in through the windows. The time isn't saved in the project: it's just for looking."
    override val apriGiorno: String get() = "The sun also changes with the seasons. Next to the slider click «📅»: usually it's today."
    override val scegliGiorno: String get() =
        "Choose a day: in winter the sun is low and reaches deeper into the rooms, in summer it's high. The day is just for looking too."

    override fun nomeMuroSoggiorno(muro: Int): String = when (muro) {
        0 -> "top"
        1 -> "right"
        2 -> "bottom-right"
        3 -> "short middle"
        4 -> "bottom-left"
        else -> "left"
    }
}
