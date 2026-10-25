package com.sagoma.planimetria.testi.en

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.testi.Suggerimenti

object SuggerimentiEn : Suggerimenti() {
    override fun titolo(v: Tip): String = when (v) {
        Tip.Welcome -> "Welcome"
        Tip.Room -> "Selected room"
        Tip.Wall -> "Wall"
        Tip.NewRoom -> "New room"
        Tip.Opening -> "Doors and windows"
        Tip.Fixture -> "Fixtures"
        Tip.Ruler -> "Ruler"
        Tip.WallCut -> "Wall under the roof"
        Tip.View3D -> "3D view"
        Tip.Stair -> "Stairs"
        Tip.Outdoor -> "Balcony and terrace"
        Tip.Structure -> "Columns and beams"
        Tip.Measure -> "Distance"
        Tip.FreeWall -> "Free wall"
        Tip.Floors -> "Floors"
        Tip.Tracing -> "Trace a floor plan"
        Tip.Calibrate -> "Calibration"
        Tip.WallDraw -> "Wall by wall"
        Tip.Underlay -> "Floor plan and drawing"
        Tip.ExcludedZone -> "Non-walkable area"
        Tip.RoomInfo -> "Info panel"
        Tip.Snap -> "Snapping"
    }

    override fun testo(v: Tip): String = when (v) {
        Tip.Welcome ->
            "Tap a room to select it. Pinch with two fingers to zoom, drag the background to move the plan. " +
                "If you make a mistake, ↶ Undo at the top puts everything back."
        Tip.Room ->
            "Drag the blue dots to move the corners and the walls to stretch it; drag the name to move the whole room. " +
                "For name, type and exact side lengths press Info or swipe up the bar at the bottom. " +
                "If snapping pulls the point where you don't want it, turn it off with 🧲 on the bar."
        Tip.Wall ->
            "Drag it to move it: it snaps to the walls of nearby rooms. From Info you change its height and, if it is shared " +
                "with another room, you can delete it to merge the two rooms into one space."
        Tip.NewRoom ->
            "The new room is attached to the open one, sharing a wall. If it belongs elsewhere drag it by its name: " +
                "it snaps to the walls of nearby rooms."
        Tip.Opening -> "Drag it along the wall to move it. From Info you change size, hinged or sliding, hinge side and opening direction, or remove it."
        Tip.Fixture ->
            "Wall fixtures slide along the wall, lights can be dragged anywhere in the room. " +
                "From Info you set height from the floor, size and rotation, or remove it."
        Tip.Ruler -> "Drag the ends to stretch it and the middle to move it. The round handle rotates it (snapping every 45°), × deletes it."
        Tip.WallCut ->
            "The wall slopes down diagonally to the height of the low wall. Drag the dot on the wall, or type the distance, " +
                "to choose where it starts sloping. The dashes show where the ceiling slope begins; in 3D you see it all."
        Tip.View3D ->
            "Drag to orbit around the house; zoom with two fingers (or the mouse wheel). Tap a wall or an object to select it. " +
                "With 🚶 Walk you go inside the rooms. All the controls are in ❔ Guides → 3D view."
        Tip.Stair ->
            "Drag it to move it: near a wall it lines up by itself. From Info choose type, width, tread and which way it turns, " +
                "and rotate it by 90°. Number and height of the steps come from the floor-to-floor height; on the floor above the stairwell appears."
        Tip.Outdoor ->
            "It is an outdoor space: instead of walls there is a parapet, against the house the house wall stays. Put it against a wall " +
                "and add a French window (Building elements → French windows) on the room wall. From Info choose railing, low wall or glass and the height."
        Tip.Structure ->
            "Drag a column to move it: near a wall it lines up and becomes a protruding pillar. A beam is moved by " +
                "dragging it and stretched from the dots at its ends, which rest on the walls. From Info you change shape and size."
        Tip.Measure ->
            "Tap the first object and then the second: walls, doors and windows, radiators, sockets, lights, columns, beams, stairs. " +
                "The distance between their nearest edges appears, with a dimension on the plan; if you move the objects it updates."
        Tip.FreeWall ->
            "A partition that does not enclose a room. Drag it to move it and drag the dots at its ends to stretch or turn it: " +
                "the ends snap to the faces of other walls and to the ends of other free walls, and the wall straightens at 90°. " +
                "From Info you change thickness, height and colour."
        Tip.Floors ->
            "With the floor button, top left, you switch between floors and add new ones. The floor below shows " +
                "in grey to line up the walls, and its stairs appear as a hole in the floor. In 3D you see the chosen floor on top of the ones below."
        Tip.Tracing ->
            "Load the photo or PDF of the floor plan, then calibrate it on a measurement you know: then you choose how to draw the rooms, " +
                "with a shape or by clicking the corners with «Wall by wall», and the measurements come by themselves, like the ones written on the plan."
        Tip.Calibrate ->
            "Tap the two ends of a measurement written on the plan (preferably the longest, for example a whole wall) and type " +
                "how many cm it is. If the measurements don't match later, \"📏 Redo calibration\" fixes it even halfway through a room."
        Tip.WallDraw ->
            "Click the corners one after the other; to finish click the first corner (circled) or \"Close room\". On top of the plan " +
                "click the inner corners of the walls: the thickness is added outwards. ↶ Last removes the last corner, " +
                "\"Type measurements\" lets you choose direction and length of each wall."
        Tip.Underlay ->
            "With the \"◐ Floor plan and drawing\" panel, bottom right, you show or hide the plan and your drawing and adjust " +
                "their transparency, to see clearly what you are tracing. The drawing is solid again when you reopen the project."
        Tip.ExcludedZone ->
            "Click the corners of the part that does not count in the m², like the low part of an attic or a shaft: the corners " +
                "snap to the inner corners of the walls. At the end choose the name; the room's m² go down, the floor stays whole."
        Tip.RoomInfo ->
            "At the top there are Duplicate, Delete, name, type and main measurements. Below, the tabs: Size (heights, thicknesses, walls and " +
                "non-walkable areas), Finishes (floor and walls), Doors, fixtures (doors, windows and fixtures), Project (existing, to demolish or new walls). " +
                "The bar on the right scrolls the content."
        Tip.Snap ->
            "With 🧲 On, corners, walls and objects snap to corners, alignments, straight directions and nearby walls. With 🧲 Off they go " +
                "exactly where you put them; the room still closes when you click the first corner."
    }
}
