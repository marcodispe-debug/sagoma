package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.model.WallPaint
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dizionario dei testi: l'italiano completo, e una traduzione che cambia solo quello che traduce. */
class TestiTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    @Test
    fun italianoComePrima() {
        assertEquals("Camera da letto", RoomType.Camera.label)
        assertEquals("Stanza", RoomType.Altro.defaultName)
        assertEquals("Benvenuto", Tip.Welcome.title)
        assertEquals("Lati cambiati: 0 di 2", T.tutorial.latiCambiati(0))
        assertEquals("Bianco puro", ProPalette.nameOf(0xFFFAFAF8))
        assertEquals(WallPaint.Sage.label, ProPalette.nameOf(WallPaint.Sage.argb))
    }

    @Test
    fun nessunTestoVuoto() {
        assertTrue(Tip.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertTrue(TutorialStep.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertTrue(ProPalette.colors.all { it.first.isNotBlank() })
    }

    @Test
    fun traduzioneParziale() {
        T.lingua = object : Lingua("en") {
            override val nomi = object : Nomi() {
                override fun roomType(v: RoomType) = if (v == RoomType.Cucina) "Kitchen" else super.roomType(v)
            }
        }
        assertEquals("Kitchen", RoomType.Cucina.label)
        // Quello che la traduzione non tocca resta in italiano.
        assertEquals("Bagno", RoomType.Bagno.label)
        assertEquals("Benvenuto", Tip.Welcome.title)
    }
}
