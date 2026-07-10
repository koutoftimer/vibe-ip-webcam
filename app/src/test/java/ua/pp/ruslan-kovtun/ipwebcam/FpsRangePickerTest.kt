package ua.pp.ruslan_kovtun.ipwebcam

import org.junit.Assert.assertEquals
import org.junit.Test

class FpsRangePickerTest {

    @Test
    fun exactMatchPreferred() {
        val available = listOf(10 to 10, 15 to 15, 30 to 30, 7 to 30, 15 to 30)
        assertEquals(30 to 30, FpsRangePicker.pick(30, available))
    }

    @Test
    fun containsDesiredWhenNoExactMatch() {
        val available = listOf(7 to 30, 15 to 30, 24 to 30)
        // tightest containing range (highest lower bound) is preferred
        assertEquals(24 to 30, FpsRangePicker.pick(30, available))
        assertEquals(7 to 30, FpsRangePicker.pick(10, available))
    }

    @Test
    fun picksSmallestUpperWhenDesiredExceedsAll() {
        val available = listOf(7 to 30, 15 to 30, 24 to 60)
        // 120 not in any range -> widest available range is used
        assertEquals(24 to 60, FpsRangePicker.pick(120, available))
    }

    @Test
    fun tightestContainingWhenNotExact() {
        val available = listOf(7 to 20, 15 to 30, 24 to 30)
        // 25 not an exact match; contained by [15,30] and [24,30]; tightest is [24,30]
        assertEquals(24 to 30, FpsRangePicker.pick(25, available))
    }

    @Test
    fun emptyListReturnsDesiredRange() {
        assertEquals(30 to 30, FpsRangePicker.pick(30, emptyList()))
    }
}
