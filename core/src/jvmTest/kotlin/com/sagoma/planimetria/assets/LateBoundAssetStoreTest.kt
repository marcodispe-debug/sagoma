package com.sagoma.planimetria.assets

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class LateBoundAssetStoreTest {
    /** Negozio di prova: risposte distinguibili per `peek`, `availability`, `ensure` e `read`. */
    private class Tag(private val tag: String) : AssetStore {
        val calls = AtomicInteger()
        override fun availability(path: String): AssetAvailability { calls.incrementAndGet(); return AssetAvailability.Remote }
        override fun peek(path: String): ByteArray? { calls.incrementAndGet(); return "peek-$tag-$path".encodeToByteArray() }
        override suspend fun ensure(path: String): AssetAvailability { calls.incrementAndGet(); return AssetAvailability.Available }
        override suspend fun read(path: String): ByteArray? { calls.incrementAndGet(); return "read-$tag-$path".encodeToByteArray() }
    }

    @Test
    fun `prima del collegamento si comporta come lo store vuoto`() = runBlocking<Unit> {
        val store = LateBoundAssetStore()
        assertEquals(AssetAvailability.Unavailable, store.availability("a"))
        assertNull(store.peek("a"))
        assertEquals(AssetAvailability.Unavailable, store.ensure("a"))
        assertNull(store.read("a"))
    }

    @Test
    fun `dopo il collegamento tutte le operazioni vanno allo stesso store`() = runBlocking<Unit> {
        val store = LateBoundAssetStore()
        val target = Tag("x")
        store.bind(target)
        assertEquals(AssetAvailability.Remote, store.availability("p"))
        assertContentEquals("peek-x-p".encodeToByteArray(), store.peek("p"))
        assertEquals(AssetAvailability.Available, store.ensure("p"))
        assertContentEquals("read-x-p".encodeToByteArray(), store.read("p"))
        assertEquals(4, target.calls.get())
    }

    @Test
    fun `un secondo collegamento e' rifiutato e non cambia lo store`() = runBlocking<Unit> {
        val store = LateBoundAssetStore()
        val first = Tag("uno"); val second = Tag("due")
        store.bind(first)
        assertFailsWith<IllegalStateException> { store.bind(second) }
        assertFailsWith<IllegalStateException> { store.bind(first) } // nemmeno lo stesso di nuovo
        assertContentEquals("peek-uno-p".encodeToByteArray(), store.peek("p"))
        assertEquals(0, second.calls.get())
    }

    @Test
    fun `non si collega a se stesso`() {
        val store = LateBoundAssetStore()
        assertFailsWith<IllegalArgumentException> { store.bind(store) }
        // il rifiuto non ha occupato il collegamento
        store.bind(Tag("x"))
    }

    @Test
    fun `collegamenti concorrenti - ne riesce uno solo e vince quello che risponde`() = runBlocking<Unit> {
        repeat(20) {
            val store = LateBoundAssetStore()
            val n = 8
            val tags = List(n) { Tag("t$it") }
            val barrier = CyclicBarrier(n)
            val done = CountDownLatch(n)
            val ok = AtomicInteger(); val rejected = AtomicInteger()
            repeat(n) { i ->
                thread {
                    barrier.await()
                    try { store.bind(tags[i]); ok.incrementAndGet() } catch (_: IllegalStateException) { rejected.incrementAndGet() }
                    done.countDown()
                }
            }
            done.await()
            assertEquals(1, ok.get()); assertEquals(n - 1, rejected.get())
            store.peek("p")
            assertEquals(1, tags.count { it.calls.get() == 1 }) // risponde uno solo, sempre lo stesso
            store.peek("p")
            assertEquals(1, tags.count { it.calls.get() == 2 })
        }
    }

    @Test
    fun `si compone con CompositeAssetStore - il primo vince, poi il collegato`() = runBlocking<Unit> {
        val late = LateBoundAssetStore()
        val composed = CompositeAssetStore(DirectoryLess("a" to "dal-bundled"), late)
        assertNull(composed.peek("b"))
        assertEquals(AssetAvailability.Unavailable, composed.availability("b"))
        late.bind(Tag("r"))
        assertContentEquals("dal-bundled".encodeToByteArray(), composed.peek("a"))
        assertContentEquals("peek-r-b".encodeToByteArray(), composed.peek("b"))
        assertSame(AssetAvailability.Remote, composed.availability("b"))
    }

    private class DirectoryLess(vararg files: Pair<String, String>) : AssetStore {
        private val map = files.toMap()
        override fun availability(path: String) = if (path in map) AssetAvailability.Available else AssetAvailability.Unavailable
        override fun peek(path: String) = map[path]?.encodeToByteArray()
    }
}
