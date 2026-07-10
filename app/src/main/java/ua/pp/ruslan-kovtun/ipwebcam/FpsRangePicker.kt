package ua.pp.ruslan_kovtun.ipwebcam

/**
 * Picks a supported AE target FPS range for a desired frame rate.
 *
 * Pure logic (no Android dependencies) so it can be unit-tested on the JVM.
 *
 * Selection order:
 * 1. an exact range [desired, desired] if available,
 * 2. a range that contains [desired] (lower <= desired <= upper), preferring the tightest,
 * 3. the range with the smallest upper bound >= desired,
 * 4. the widest available range as a last resort.
 */
object FpsRangePicker {

    fun pick(desired: Int, available: List<Pair<Int, Int>>): Pair<Int, Int> {
        if (available.isEmpty()) return desired to desired

        available.firstOrNull { it.first == desired && it.second == desired }?.let { return it }

        available.filter { it.first <= desired && desired <= it.second }
            .maxByOrNull { it.first }?.let { return it }

        available.filter { it.second >= desired }.minByOrNull { it.second }?.let { return it }

        return available.maxByOrNull { it.second } ?: (desired to desired)
    }
}
