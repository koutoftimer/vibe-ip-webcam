package ua.pp.ruslan_kovtun.ipwebcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun acquireSetsInitialRefCountSoSingleReleaseReturnsBuffer() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        pool.release(buf) // refCount drops to 0 -> back in the free list
        val reused = pool.acquire(1024)
        assertSame("first acquire must leave buffer freeable", buf, reused)
    }

    @Test
    fun releasedBufferIsReusedForEqualOrSmallerSize() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(2048)
        pool.release(buf)

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
        val other = pool.acquire(1024)
        assertNotSame("still-referenced buffer must not be reused", buf, other)

        pool.release(buf) // client releases -> refCount 0 -> returned
        val reused = pool.acquire(1024)
        assertSame("fully released buffer is reused", buf, reused)
    }

    @Test
    fun doubleReleaseDoesNotCorruptPool() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        pool.release(buf)
        pool.release(buf) // extra release must be a no-op

        val reused = pool.acquire(1024)
        assertSame("single pooled buffer is reused", buf, reused)
    }

    @Test
    fun poolBoundsFreeListSize() {
        val pool = FrameBufferPool(maxPooled = 2)
        val a = pool.acquire(1024)
        val b = pool.acquire(1024)
        val c = pool.acquire(1024)
        pool.release(a)
        pool.release(b)
        pool.release(c) // dropped: free list is capped at 2

        val r1 = pool.acquire(1024)
        val r2 = pool.acquire(1024)
        val r3 = pool.acquire(1024)

        val reused = listOf(r1, r2, r3)
        assertTrue("buffer a should be reused", reused.contains(a))
        assertTrue("buffer b should be reused", reused.contains(b))
        assertTrue("dropped buffer c must never be reused", !reused.contains(c))
        assertFalse("exactly two buffers fit the free list", reused[0] == reused[1] && reused[1] == reused[2])
    }

    /**
     * Simulates the pushFrame lifecycle when no client is connected:
     *   CameraController.acquire → pushFrame (store, no retain) → next pushFrame (release previous)
     * The buffer must be recycled after pushFrame replaces it.
     */
    @Test
    fun bufferRecycledAfterPushFrameWithoutClient() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        // pushFrame stores buf as latestFrame (no retain — the fix)
        // Next pushFrame arrives, releases previous:
        pool.release(buf)
        val reused = pool.acquire(1024)
        assertSame("buffer must be recycled when pushFrame replaces it", buf, reused)
    }

    /**
     * Simulates the full pushFrame + serveMjpeg lifecycle:
     *   acquire → pushFrame(store) → serveMjpeg(retain, write, release) → next pushFrame(release previous)
     * All refCounts must balance so the buffer returns to the pool.
     */
    @Test
    fun bufferRecycledAfterFullServeMjpegCycle() {
        val pool = FrameBufferPool()
        val buf = pool.acquire(1024)
        // serveMjpeg picks up latestFrame and retains it
        pool.retain(buf)
        // serveMjpeg finishes writing and releases
        pool.release(buf)
        // Next pushFrame replaces this frame, releases it
        pool.release(buf)
        val reused = pool.acquire(1024)
        assertSame("buffer must be recycled after full serveMjpeg + pushFrame cycle", buf, reused)
    }

    /**
     * Simulates continuous streaming with a client: 100 frames pushed and served.
     * Every buffer must be recycled — no new allocations after the pool is warmed up.
     *
     * Real lifecycle per frame:
     *   pushFrame releases previous → acquire new → serveMjpeg retain → serveMjpeg release
     */
    @Test
    fun noLeakDuringContinuousStreaming() {
        val pool = FrameBufferPool()
        // Warm up: acquire one buffer, release it back
        val warmup = pool.acquire(1024)
        pool.release(warmup)

        var previous: ByteArray? = null
        for (i in 1..100) {
            // pushFrame releases the previous frame (refCount 0 → pool)
            if (previous != null) pool.release(previous)
            // CameraController acquires a buffer (reuse from pool)
            val buf = pool.acquire(1024)
            assertSame("frame $i must reuse the pooled buffer", warmup, buf)
            // serveMjpeg retains then releases
            pool.retain(buf)
            pool.release(buf)
            previous = buf
        }
        // Release the last frame
        pool.release(previous!!)
    }
}