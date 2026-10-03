package com.sagoma.planimetria.assets

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** `CompositeAssetStore` con gli stati aggiunti ([AssetAvailability.Remote], [AssetAvailability.Incompatible]). */
class AvailabilityAggregationTest {
    private class Fixed(val a: AssetAvailability, val ensured: AssetAvailability = a, val bytes: ByteArray? = null) : AssetStore {
        override fun availability(path: String) = a
        override fun peek(path: String) = bytes
        override suspend fun ensure(path: String) = ensured
    }

    private val all = AssetAvailability.entries

    @Test
    fun `Available vince su tutto qualunque sia l'ordine`() {
        for (other in all) {
            assertEquals(AssetAvailability.Available, CompositeAssetStore(Fixed(other), Fixed(AssetAvailability.Available)).availability("x"))
            assertEquals(AssetAvailability.Available, CompositeAssetStore(Fixed(AssetAvailability.Available), Fixed(other)).availability("x"))
            runBlocking {
                assertEquals(AssetAvailability.Available, CompositeAssetStore(Fixed(other), Fixed(AssetAvailability.Available)).ensure("x"))
            }
        }
    }

    @Test
    fun `tra gli stati non disponibili vince il migliore`() {
        val U = AssetAvailability.Unavailable
        val R = AssetAvailability.Remote
        val I = AssetAvailability.Incompatible
        fun av(a: AssetAvailability, b: AssetAvailability) = CompositeAssetStore(Fixed(a), Fixed(b)).availability("x")
        assertEquals(R, av(U, R)); assertEquals(R, av(R, U)); assertEquals(R, av(I, R)); assertEquals(R, av(R, I))
        assertEquals(I, av(U, I)); assertEquals(I, av(I, U))
        assertEquals(U, av(U, U))
        assertEquals(R, av(R, R))
    }

    @Test
    fun `ensure riassume gli stati dopo aver provato ogni negozio`() = runBlocking {
        val s = CompositeAssetStore(
            Fixed(AssetAvailability.Unavailable),
            Fixed(AssetAvailability.Remote, ensured = AssetAvailability.Incompatible),
            Fixed(AssetAvailability.Remote, ensured = AssetAvailability.Available),
        )
        assertEquals(AssetAvailability.Available, s.ensure("x"))
        val s2 = CompositeAssetStore(Fixed(AssetAvailability.Remote, ensured = AssetAvailability.Incompatible), Fixed(AssetAvailability.Unavailable))
        assertEquals(AssetAvailability.Incompatible, s2.ensure("x"))
    }

    @Test
    fun `nessun negozio e vuoto e il comportamento a due stati e invariato`() {
        assertEquals(AssetAvailability.Unavailable, CompositeAssetStore(emptyList()).availability("x"))
        val s = CompositeAssetStore(EmptyAssetStore, EmptyAssetStore)
        assertEquals(AssetAvailability.Unavailable, s.availability("x"))
        runBlocking { assertEquals(AssetAvailability.Unavailable, s.ensure("x")) }
        assertEquals(false, CompositeAssetStore(Fixed(AssetAvailability.Remote)).isAvailable("x"))
    }

    @Test
    fun `solo Available si legge`() = runBlocking {
        val s = CompositeAssetStore(Fixed(AssetAvailability.Remote, bytes = byteArrayOf(1)))
        assertEquals(null, s.read("x")) // il default di read chiede ensure() == Available; qui il composito prova read di ogni negozio
    }
}
