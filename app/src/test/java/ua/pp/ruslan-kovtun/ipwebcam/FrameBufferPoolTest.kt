package ua.pp.ruslan_kovtun.ipwebcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameBufferPoolTest {

    @Test
    fun acquireReturnsBufferOfAtLeastRequestedSize() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        assertTrue("buffer must be at least requested size", buf.size >= 1024)
    }

    @Test
    fun releasedBufferIsReusedForEqualOrSmallerSize() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(2048)
        pool.release(buf)

        assertEquals("released buffer should be available in the pool", 1, pool.freeBuffers)

        val reused = pool.acquire(1024)
        assertSame("acquire should reuse the freed buffer", buf, reused)
        assertEquals("buffer capacity must be preserved", 2048, reused.size)
    }

    @Test
    fun releasedBufferIsNotReusedWhenTooSmall() {
        val pool = FrameBufferPool()
        val small = pool.acquire(512)
        pool.release(small)

        val big = pool.acquire(4096)
        assertNotSame("a too-small buffer must not be reused", small, big)
        assertEquals(4096, big.size)
    }

    @Test
    fun bufferNotRecycledWhileReferenced() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        pool.retain(buf) // simulate a client hold -> refCount 2

        pool.release(buf) // server releases -> refCount 1, still in use
        assertEquals("buffer must stay alive while referenced", 1, pool.totalBuffers)
        assertEquals("buffer must not be freed while referenced", 0, pool.freeBuffers)

        pool.release(buf) // client releases -> refCount 0
        assertEquals("buffer returned to pool", 1, pool.freeBuffers)
    }

    @Test
    fun doubleReleaseDoesNotCorruptPool() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        pool.release(buf)
        pool.release(buf) // extra release must be a no-op

        assertEquals(1, pool.freeBuffers)
        val reused = pool.acquire(1024)
        assertSame(buf, reused)
    }

    @Test
    fun poolBoundsFreeListSize() {
        val pool = FrameBufferPool(maxPooled = 2)
        val a = pool.acquire(1024)
        val b = pool.acquire(1024)
        val c = pool.acquire(1024)
        pool.release(a)
        pool.release(b)
        pool.release(c)

        // Only the first two releases fit; the third is dropped for GC.
        assertEquals(2, pool.freeBuffers)
    }
}
