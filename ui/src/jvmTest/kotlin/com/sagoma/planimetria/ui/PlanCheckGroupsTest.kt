package com.sagoma.planimetria.ui

import com.sagoma.planimetria.editor.PlanCheck
import com.sagoma.planimetria.editor.PlanCheck.Kind
import com.sagoma.planimetria.geometry.Bounds
import com.sagoma.planimetria.testi.T
import kotlin.test.Test
import kotlin.test.assertEquals

/** «Controlla la pianta»: quali gruppi sono aperti all'inizio. */
class PlanCheckGroupsTest {
    private fun issues(kind: Kind, n: Int) = List(n) { PlanCheck.Issue(kind, 0, listOf(it + 1L), Bounds(0.0, 0.0, 100.0, 100.0)) }

    @Test
    fun `aperti il primo gruppo e quelli corti`() {
        // Tanti errori: il primo gruppo resta aperto anche se lungo; i controlli lunghi si chiudono, le norme corte no.
        val groups = PlanCheck.groups(issues(Kind.Overlap, 12) + issues(Kind.NoHeating, OPEN_GROUP_MAX + 1) + issues(Kind.NoWindows, OPEN_GROUP_MAX))
        assertEquals(
            mapOf(T.controllo.daCorreggere to true, T.controllo.daControllare to false, T.controllo.daNorme to true),
            openAtStart(groups),
        )
        // Senza errori il primo gruppo è quello dei controlli: aperto anche se lungo.
        val checks = PlanCheck.groups(issues(Kind.NoHeating, 30) + issues(Kind.NoWindows, 30))
        assertEquals(mapOf(T.controllo.daControllare to true, T.controllo.daNorme to false), openAtStart(checks))
        assertEquals(emptyMap(), openAtStart(PlanCheck.groups(emptyList())))
    }
}
