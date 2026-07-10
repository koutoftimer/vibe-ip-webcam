package ua.pp.ruslan_kovtun.ipwebcam

import java.util.ArrayDeque

/**
 * A thread-safe pool of reusable [ByteArray] buffers used to avoid per-frame
 * heap allocations in the MJPEG pipeline.
 *
 * Each buffer carries a reference count. A buffer is only returned to the free
 * pool (and thus eligible for reuse) once its reference count reaches zero.
 * This guarantees a buffer is never overwritten while a producer or a client
 * thread is still reading from it.
 *
 * Thread-safety: all mutating operations are guarded by the instance lock.
 */
class FrameBufferPool(private val maxPooled: Int = 3) {

    private val free = ArrayDeque<ByteArray>()
    private val refCounts = HashMap<ByteArray, Int>()

    /**
     * Returns a buffer with capacity >= [size]. Prefers reusing a free buffer
     * large enough to hold the data; otherwise allocates a new one. The returned
     * buffer has a reference count of 1 (the caller's hold).
     */
    fun acquire(size: Int): ByteArray = synchronized(this) {
        val buf = free.firstOrNull { it.size >= size }?.also { free.remove(it) } ?: ByteArray(size)
        refCounts[buf] = 1
        buf
    }

    /** Increments the reference count of [buf]. */
    fun retain(buf: ByteArray) = synchronized(this) {
        refCounts[buf] = (refCounts[buf] ?: 0) + 1
    }

    /**
     * Decrements the reference count of [buf]. When it reaches zero the buffer
     * is returned to the free pool if there is room, otherwise it is dropped and
     * left for the garbage collector.
     */
    fun release(buf: ByteArray) = synchronized(this) {
        val current = refCounts[buf] ?: 0
        if (current <= 0) return // already recycled or never tracked
        val next = current - 1
        if (next <= 0) {
            refCounts.remove(buf)
            if (free.size < maxPooled) free.addLast(buf)
        } else {
            refCounts[buf] = next
        }
    }
}
