package com.sagoma.planimetria.access

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RightsTest {
    private val pro = Account(profile = Profile.Professional)

    @Test
    fun `il watermark si vede senza piano professionale, anche con acquisti`() {
        assertTrue(Rights.showsWatermark(Account.Guest))
        assertTrue(Rights.showsWatermark(Account(purchases = setOf(OutputPurchase(1, OutputFormat.Plan)))))
        assertFalse(Rights.showsWatermark(pro))
    }

    @Test
    fun `uscita pulita per il professionista o se ogni stanza e acquistata in quel formato`() {
        val bought = Account(purchases = setOf(OutputPurchase(1, OutputFormat.Plan), OutputPurchase(2, OutputFormat.Plan)))
        assertTrue(Rights.cleanOutput(pro, OutputFormat.View3D, listOf(1, 2, 3)))
        assertTrue(Rights.cleanOutput(bought, OutputFormat.Plan, listOf(1, 2)))
        assertFalse(Rights.cleanOutput(bought, OutputFormat.Plan, listOf(1, 2, 3)))
        // Planimetria e vista 3D della stessa stanza sono due acquisti distinti.
        assertFalse(Rights.cleanOutput(bought, OutputFormat.View3D, listOf(1)))
        assertFalse(Rights.cleanOutput(Account.Guest, OutputFormat.Plan, listOf(1)))
        assertFalse(Rights.cleanOutput(bought, OutputFormat.Plan, emptyList()))
    }

    @Test
    fun `inserire arredi per ora e aperto a tutti`() {
        assertTrue(Rights.has(Account.Guest, Right.InsertFurniture))
        assertTrue(Rights.has(pro, Right.InsertFurniture))
    }
}
