package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.Course
import com.sagoma.planimetria.editor.CourseStep
import com.sagoma.planimetria.editor.EditorViewModel
import com.sagoma.planimetria.editor.GuidePlan
import com.sagoma.planimetria.editor.Lesson
import com.sagoma.planimetria.editor.TipStore
import com.sagoma.planimetria.geometry.ShapeDimensions
import com.sagoma.planimetria.model.Building
import com.sagoma.planimetria.model.FloorPlan
import com.sagoma.planimetria.model.LOrientation
import com.sagoma.planimetria.model.RoomShape
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.PlanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Guida interattiva nell'editor: ogni azione fa avanzare il passo giusto, e la guida non lascia parlare il tutorial di una volta. */
class CourseTest {
    private class MemoryTips(var seen: Set<String> = emptySet()) : TipStore {
        override fun seen() = seen
        override fun save(seen: Set<String>) { this.seen = seen }
    }

    private fun emptyProject(tips: TipStore = MemoryTips()) = EditorViewModel(object : PlanStore {
        override fun load() = Building.single(FloorPlan(), autoLevel = true)
        override fun save(building: Building) {}
    }, tips).also { it.setViewport(1400f, 750f, 24f) }

    private val EditorViewModel.step get() = state.value.course?.step

    private fun EditorViewModel.square(type: RoomType, side: Int) {
        startAddRoom()
        pickShape(RoomShape.Square)
        createRoom(type, ShapeDimensions(side.toDouble(), side.toDouble()), 270.0)
    }

    @Test
    fun `le prime lezioni avanzano con le azioni e il tutorial di una volta non parte`() {
        val tips = MemoryTips()
        val vm = emptyProject(tips)
        vm.startCourse(Lesson.Square)
        assertEquals(CourseStep.Welcome, vm.step)
        vm.nextCourseStep()
        assertEquals(CourseStep.SquarePick, vm.step)
        vm.pickShape(RoomShape.Rectangle)
        assertEquals(CourseStep.SquarePick, vm.step, "la forma sbagliata non fa avanzare")
        vm.backToShapePicker()
        vm.pickShape(RoomShape.Square)
        assertEquals(CourseStep.SquareMeasures, vm.step)
        vm.createRoom(RoomType.Studio, ShapeDimensions(330.0, 330.0), 270.0)
        assertEquals(CourseStep.SquareDone, vm.step)
        assertNull(vm.state.value.tutorial, "la prima stanza non fa partire il tutorial di una volta")
        assertNull(vm.state.value.tip, "durante la guida niente suggerimenti")

        vm.nextCourseStep()
        vm.startAddRoom()
        vm.pickShape(RoomShape.Rectangle)
        vm.createRoom(RoomType.Camera, ShapeDimensions(400.0, 330.0), 270.0)
        assertEquals(CourseStep.RectDone, vm.step)
        vm.nextCourseStep()
        vm.startAddRoom()
        vm.pickShape(RoomShape.L)
        assertEquals(CourseStep.LSide, vm.step)
        vm.pickLOrientation(LOrientation.BottomRight)
        vm.createRoom(RoomType.Soggiorno, ShapeDimensions(640.0, 270.0, 300.0, 190.0), 270.0)
        assertEquals(CourseStep.LDone, vm.step)
        vm.nextCourseStep()
        vm.startAddRoom()
        vm.pickSurvey()
        assertEquals(CourseStep.SurveyMeasures, vm.step)
        vm.square(RoomType.Bagno, 260) // anche una stanza fatta in un altro modo chiude il passo delle misure
        assertEquals(CourseStep.SurveyDone, vm.step)
        vm.nextCourseStep()
        vm.startAddRoom()
        vm.startDrawWalls()
        assertEquals(CourseStep.WallsFirst, vm.step)

        // L'inquadratura del primo muro lascia vedere tutto l'ingresso da disegnare, sotto le stanze già fatte.
        val hint = Course.hints(CourseStep.WallsFirst, vm.state.value).single()
        val start = hint.marks.single().points.single()
        val cam = vm.state.value.camera
        val bottom = cam.toScreenY(start.y + GuidePlan.side(GuidePlan.ingresso, 1))
        assertTrue(bottom < 750f, "fondo dell'ingresso a $bottom px, fuori dalla vista")

        vm.finishCourse(completed = false)
        assertNull(vm.step)
        assertTrue(TipStore.COURSE_DONE in tips.seen && TipStore.TUTORIAL_DONE in tips.seen)
    }
}
