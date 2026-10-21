package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Passo
import com.sagoma.planimetria.testi.TestiGuide

object TestiGuideEn : TestiGuide() {
    override val titoloMansarda: String get() = "Attic"
    override val titoloRicalco: String get() = "Trace a floor plan"
    override val titoloStanze: String get() = "Rooms and measurements"
    override val titoloZone: String get() = "Non-walkable areas"
    override val titoloMuri: String get() = "Walls and corners"
    override val titoloAgganci: String get() = "Snapping"
    override val titoloPorte: String get() = "Doors and windows"
    override val titoloImpianti: String get() = "Fixtures"
    override val titoloPiuStanze: String get() = "More rooms"
    override val titoloBalconi: String get() = "Balconies and terraces"
    override val titoloScale: String get() = "Stairs and floors"
    override val titoloVista3D: String get() = "3D view"
    override val titoloQuote: String get() = "Dimensions and text"

    override fun passoDi(numero: Int, totale: Int): String = "Step $numero of $totale"
    override val apriVista3D: String get() = "Open the 3D view"

    override val mansardaIntro: Passo get() = Passo(
        "What an attic looks like",
        "In an attic the ceiling slopes down towards a lower wall (the \"knee wall\" under the roof pitch). " +
            "So the side walls are not rectangular: they are full height up to a point and then drop " +
            "diagonally. In this app you get it in two moves: you lower the knee wall and cut the walls next to it diagonally.",
    )

    override val mansardaStanza: Passo get() = Passo(
        "1 · Draw the room",
        "Create the room with «🧱 Building elements» → Room and adjust the measurements from the Info panel, as for any other room. " +
            "The room's ceiling height is the height of the highest part of the attic.",
    )

    override val mansardaMuretto: Passo get() = Passo(
        "2 · Lower the knee wall",
        "Tap the wall the roof slopes towards, open Info and type its real height in \"Wall height\" " +
            "(for example 120 cm). On the plan the height appears in purple next to the wall.",
    )

    override val mansardaTaglio: Passo get() = Passo(
        "3 · Cut the first side wall",
        "Tap one of the two walls perpendicular to the knee wall and, in the Info panel, turn on \"Diagonal cut (under the roof)\". " +
            "If the knee wall is lower, the wall now slopes down to its height. If both walls next to it are low, " +
            "choose the side in \"Slopes towards\".",
    )

    override val mansardaInizio: Passo get() = Passo(
        "4 · Choose where it starts sloping",
        "Measure in the house how far from the knee wall the ceiling starts to slope and type it in \"Starts sloping at\". " +
            "Or drag the orange dot on the plan: the dashed line marks where the slope begins.",
    )

    override val mansardaRipeti: Passo get() = Passo(
        "5 · Repeat on the other wall",
        "Do the same on the opposite wall, so the ceiling slopes evenly across the whole room. " +
            "If the wall is shared with another room, only the attic side is cut: the room next door stays whole.",
    )

    override val mansardaControllo: Passo get() = Passo(
        "6 · Check doors, windows and fixtures",
        "If a window, a door, a radiator or a socket ends up higher than the sloping wall, a " +
            "red dot with \"!\" appears on the plan and a warning in the Info panel: lower the object or move it along the wall " +
            "towards the high part.",
    )

    override val mansarda3D: Passo get() = Passo(
        "7 · See the result in 3D",
        "Open the 3D view: the top of the walls and the ceiling slope diagonally and you can walk inside the attic " +
            "to check that everything adds up. The room volume in the Info panel already takes the sloping ceiling into account.",
    )

    override val caricaPiantina: Passo get() = Passo(
        "Load the floor plan",
        "In a new project choose «Trace a floor plan»; in a project already started use ⋮ → «Background plan…». " +
            "Then «Photo / image» (or «PDF» on the computer): land registry, architect's plan or a straight photo.",
    )

    override val ricalcaAngoli: Passo get() = Passo(
        "Trace the corners",
        "«Wall by wall» opens. " + Tip.WallDraw.text + " For the next room: «🧱 Building elements» → Room → «Wall by wall».",
    )

    override val controllaMisure: Passo get() = Passo(
        "Check the measurements",
        "Open the room Info and compare m² and walls with the ones written on the plan. If a part does not count in the m² " +
            "(low attic, shaft) add a non-walkable area.",
    )

    override val muoversi: Passo get() = Passo(
        "Moving around the plan",
        Tip.Welcome.text,
    )

    override val aggiungiZona: Passo get() = Passo(
        "Add an area",
        "Select the room, open Info and, in the Size tab, press «▧ Add non-walkable area».",
    )

    override val agganci: Passo get() = Passo(
        "Turn snapping on and off",
        "The 🧲 button is on the room Info bar and in the «Wall by wall» panel. " + Tip.Snap.text,
    )

    override val aggiungiBalcone: Passo get() = Passo(
        "Add a balcony or terrace",
        "Press «🧱 Building elements» → Balconies and terraces and choose the type, then shape and size as for a room. It starts already attached to the house: " +
            "drag it by its name along the wall to the right spot, it snaps by itself. On the outward sides there is the parapet, " +
            "against the house the wall stays.",
    )

    override val parapetto: Passo get() = Passo(
        "Parapet and French window",
        Tip.Outdoor.text,
    )

    override val aggiungiScala: Passo get() = Passo(
        "Add stairs",
        "Press «🧱 Building elements» → Stairs and choose the type: straight flight, L-shaped with landing, U-shaped with two flights or spiral. " +
            "The stairs appear in the middle of the view (or of the selected room): drag them where needed, near a wall they line up by themselves.",
    )

    override val opzioniScala: Passo get() = Passo(
        "Type, size and direction",
        "Tap the stairs and open Info: you change type, width, tread (or spiral diameter), which way they turn and the rotation, " +
            "or turn them by 90° with ↻. Number and height of the steps come from the floor-to-floor height; if the steps are uncomfortable a warning appears.",
    )

    override val aggiungiPiano: Passo get() = Passo(
        "Add a floor",
        "Tap the floor button top left and choose «Add a floor above». You can start from the walls of the floor below " +
            "or from an empty floor. The floor below stays visible in grey and its stairs appear as a hole in the floor.",
    )

    override val piani3D: Passo get() = Passo(
        "Floors in 3D",
        "In 3D you see the chosen floor on top of the ones below, connected by the stairs; higher floors are hidden, as if " +
            "the roof were taken off. From the same floor menu you change its name and floor-to-floor height, or delete it.",
    )

    override val comandi3D: Passo get() = Passo(
        "All the controls",
        "Phone: one finger rotates, two fingers zoom and pan; a selected wall, door or fixture can be dragged to " +
            "move it. In 🚶 Walk you move with the joystick bottom left. Computer: ↑ ↓ (or W S) forward and back, ← → to " +
            "turn, A D to step sideways, PgUp/PgDn to look up or down, Shift to go faster; " +
            "mouse wheel to zoom, right button drag to pan the view.",
    )

    override val quota: Passo get() = Passo(
        "Add a dimension",
        "Press «🛠 Tools» → Dimension and tap the two ends: the points snap to corners and wall faces. The dimension stays " +
            "on the plan and appears in the sheets; tap it to move or delete it.",
    )

    override val testo: Passo get() = Passo(
        "Write a text",
        "Press «🛠 Tools» → Text, tap where to write it, then type and choose the size.",
    )
}
