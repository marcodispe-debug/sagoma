package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.testi.TestiTutorial

object TestiTutorialEn : TestiTutorial() {
    override fun titolo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro -> "Tutorial"
        TutorialStep.OpenInfo -> "Room information"
        TutorialStep.WallLengths -> "Change the measurements"
        TutorialStep.ChangeType -> "Room type"
        TutorialStep.MoveWall -> "Move a wall"
        TutorialStep.MoveCorner -> "Move a corner"
        TutorialStep.AddDoor -> "Add a door"
        TutorialStep.MoveDoor -> "Move the door"
        TutorialStep.AddFixture -> "Fixtures"
        TutorialStep.AddRoom -> "Add a room"
        TutorialStep.MoveRoom -> "Join the rooms"
        TutorialStep.Undo -> "Undo"
        TutorialStep.Done -> "Done!"
        TutorialStep.TraceLoad -> "Load the floor plan"
        TutorialStep.TraceCalibA -> "Calibration: the first point"
        TutorialStep.TraceCalibB -> "Calibration: the second point"
        TutorialStep.TraceCalibValue -> "Calibration: the real length"
        TutorialStep.TracePick -> "Choose how to draw"
        TutorialStep.TraceFirstCorner -> "The first corner"
        TutorialStep.TraceCorners -> "The other corners"
        TutorialStep.TraceClose -> "Close the room"
        TutorialStep.TraceCheck -> "Check the measurements"
        TutorialStep.TraceFade -> "Floor plan and drawing"
        TutorialStep.TraceNext -> "The next room"
        TutorialStep.TraceDone -> "Done!"
    }

    override fun testo(v: TutorialStep): String = when (v) {
        TutorialStep.Intro ->
            "I'll guide you step by step through the main features: each step asks you to try it yourself. " +
                "These are real changes to the plan, but at the end you can undo them with ↶."
        TutorialStep.OpenInfo ->
            "Tap a room to select it: the Info bar appears at the bottom. Press Info, the ▲ arrow " +
                "or swipe the bar up to open its properties."
        TutorialStep.WallLengths ->
            "In the panel, under «Walls», there is the length of each side: type a new value and confirm. " +
                "The opposite side adjusts by itself. Then change a side in the other direction. The dimensions on the plan update."
        TutorialStep.ChangeType -> "Still in the panel, choose a room type, for example Kitchen: colour and name change by themselves."
        TutorialStep.MoveWall ->
            "Close the panel (▼ or swipe down). Then tap a wall to select it and drag it: " +
                "the room gets bigger or smaller, and the wall snaps to the walls of nearby rooms."
        TutorialStep.MoveCorner -> "Drag one of the blue dots at the corners of the room: you can even make the walls slanted."
        TutorialStep.AddDoor -> "Press «🧱 Building elements» → Doors, choose a type and then tap the wall to put it on."
        TutorialStep.MoveDoor -> "Drag the door along the wall. From Info you change its size and opening direction, or make it sliding."
        TutorialStep.AddFixture -> "Press «🧱 Building elements» → Fixtures and choose, for example, Power socket, then tap a wall. Lights instead are placed by tapping a spot inside the room."
        TutorialStep.AddRoom -> "Press «🧱 Building elements» → Room, choose shape and size and confirm."
        TutorialStep.MoveRoom -> "Drag the new room by its name and bring it close to another one: when the walls are close they snap together."
        TutorialStep.Undo -> "Made a mistake? Press ↶ Undo at the top to go back one step (↷ Redo does it again)."
        TutorialStep.Done -> "Now you know the main features. From «❔ Guides», top right, you can redo the tutorial whenever you want and find a guide for every feature."
        TutorialStep.TraceLoad -> "Press «Photo / image» (or «PDF» on the computer) and choose the floor plan of the house: land registry, architect's plan or a straight photo."
        TutorialStep.TraceCalibA ->
            "Look on the plan for a written measurement, preferably a long one (for example a wall marked 4.72). " +
                "Zoom in and tap the start of that measurement: point A appears."
        TutorialStep.TraceCalibB -> "Now tap the end of the same measurement: point B appears. If you get it wrong, another tap starts again from A."
        TutorialStep.TraceCalibValue ->
            "In the box at the top type how long the measurement between A and B really is (in cm, as on the plan) and confirm: " +
                "the plan is now to scale."
        TutorialStep.TracePick ->
            "The calibration is saved: the plan is to scale. Now choose how to draw the rooms: with a shape (Square, Rectangle, " +
                "L-shaped…) you create it with its measurements and drag it by its name onto the plan; with «Wall by wall» you click " +
                "its corners on the plan, one after the other."
        TutorialStep.TraceFirstCorner ->
            "Now you are in «Wall by wall». Choose a room and click one of its corners, on the inner side of the wall: " +
                "the wall thickness is added outwards by itself."
        TutorialStep.TraceCorners -> "Click the other corners one after the other, going around the room. If you get one wrong, «↶ Last» removes the last corner."
        TutorialStep.TraceClose ->
            "When you have clicked all the corners, click the first one again (the circled one) or press «Close room»; " +
                "then choose the room type and confirm."
        TutorialStep.TraceCheck ->
            "Open the room Info (bar at the bottom, ▲ or swipe up): compare m² and walls with the plan. " +
                "If a part does not count in the m² (low attic, shaft) use «▧ Add non-walkable area» in the Size tab."
        TutorialStep.TraceFade ->
            "Close the panel and press «◐ Floor plan and drawing» bottom right: make the plan lighter or hide it " +
                "to see the drawing clearly."
        TutorialStep.TraceNext -> "Press «🧱 Building elements» → Room and trace the next room, with a shape or with «Wall by wall», as before."
        TutorialStep.TraceDone ->
            "Carry on like this with the other rooms. If snapping moves your points, turn it off with 🧲. " +
                "With «Done», if you haven't done it yet, the tutorial for the other features starts (doors, fixtures, measurements)."
    }

    override fun latiCambiati(n: Int): String =
        if (n == 0) "Sides changed: 0 of 2" else "Sides changed: 1 of 2 · now a side in the other direction"
    override fun angoliServono(n: Int): String = "Corners clicked: $n · at least 3 needed"
    override fun angoliCliccati(n: Int): String = "Corners clicked: $n"
}
