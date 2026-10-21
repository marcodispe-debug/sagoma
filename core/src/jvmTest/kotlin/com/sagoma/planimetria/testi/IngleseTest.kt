package com.sagoma.planimetria.testi

import com.sagoma.planimetria.editor.Tip
import com.sagoma.planimetria.editor.TutorialStep
import com.sagoma.planimetria.geometry.formatDecimal
import com.sagoma.planimetria.model.Dimension
import com.sagoma.planimetria.model.ProPalette
import com.sagoma.planimetria.model.RoomType
import com.sagoma.planimetria.persistence.LanguageChoice
import com.sagoma.planimetria.testi.en.Inglese
import java.lang.reflect.Modifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Inglese: ogni testo dell'italiano ha la sua traduzione, e la scelta della lingua segue quella dell'utente o del sistema. */
class IngleseTest {
    @AfterTest
    fun italiano() {
        T.lingua = Italiano
    }

    private val aree = Lingua::class.java.declaredMethods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && Modifier.isPublic(it.modifiers) && !it.returnType.isPrimitive }

    @Test
    fun ogniAreaHaLaSuaTraduzione() {
        for (getter in aree) {
            val italiano = getter.invoke(Italiano)
            val inglese = getter.invoke(Inglese)
            assertTrue(italiano !== inglese, "${getter.name}: l'inglese usa ancora l'italiano")
        }
    }

    @Test
    fun ogniTestoETradotto() {
        val mancanti = mutableListOf<String>()
        for (getter in aree) {
            val base = getter.returnType
            val tradotta = getter.invoke(Inglese).javaClass
            for (m in base.declaredMethods) {
                if (!Modifier.isPublic(m.modifiers) || Modifier.isStatic(m.modifiers) || m.isSynthetic) continue
                val ok = runCatching { tradotta.getDeclaredMethod(m.name, *m.parameterTypes) }.isSuccess
                if (!ok) mancanti += "${base.simpleName}.${m.name}"
            }
        }
        assertTrue(mancanti.isEmpty(), "testi senza traduzione: $mancanti")
    }

    @Test
    fun testiInInglese() {
        T.lingua = Inglese
        assertEquals("Bedroom", RoomType.Camera.label)
        assertEquals("Room", RoomType.Altro.defaultName)
        assertEquals("Welcome", Tip.Welcome.title)
        assertEquals("Sides changed: 0 of 2", T.tutorial.latiCambiati(0))
        assertEquals("Pure white", ProPalette.nameOf(0xFFFAFAF8))
        assertTrue(Tip.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertTrue(TutorialStep.entries.all { it.title.isNotBlank() && it.text.isNotBlank() })
        assertEquals("20.50", formatDecimal(20.5, 2))
        assertEquals("12.5", Dimension.formatLength(12.5))
        assertEquals(Italiano.nomi.tavolozza.keys, Inglese.nomi.tavolozza.keys, "la tavolozza ha gli stessi colori")
    }

    @Test
    fun sceltaDellaLingua() {
        assertSame(Italiano, Lingue.scegli(LanguageChoice.Italian, "en-US"))
        assertSame(Inglese, Lingue.scegli(LanguageChoice.English, "it-IT"))
        assertSame(Italiano, Lingue.scegli(LanguageChoice.System, "it-IT"))
        assertSame(Italiano, Lingue.scegli(LanguageChoice.System, "it"))
        assertSame(Inglese, Lingue.scegli(LanguageChoice.System, "en-GB"))
        assertSame(Inglese, Lingue.scegli(LanguageChoice.System, "de"))
    }
}
