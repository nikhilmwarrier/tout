package com.tout.app

import org.junit.Assert.*
import org.junit.Test

// ponytail: one check for the trust boundary (import parser) — fails if round-trip breaks
class StoreTest {
    @Test
    fun roundTripAndRejectsBadLines() {
        val e = Entry(date = "2026-09-25", type = "money", amount = 30.0, tags = listOf("Cafe BBG", "Samosa"))
        val back = Store.parseLine(Store.toJson(e))
        assertNotNull(back)
        assertEquals("money", back!!.type)
        assertEquals(30.0, back.amount!!, 0.0)
        assertEquals(listOf("Cafe BBG", "Samosa"), back.tags)

        assertNull(Store.parseLine("""{"type":"bogus","date":"2026-09-25"}"""))
        assertNull(Store.parseLine("""{"type":"money"}""")) // missing date
        assertNull(Store.parseLine("not json at all"))
    }
}

// ponytail: one check for the dial snap — chosen journal must land exactly at top
class DialMathTest {
    @Test
    fun snapLandsAtTop() {
        val bases = listOf(-90f, 30f, 150f)
        // ponytail: large rotations regress the Kotlin-%-keeps-sign bug (wrong detent past ±630°)
        listOf(0f, 37f, -200f, 720f, -45.5f, 800f, -800f, 1080f, -1080f).forEach { rot ->
            val i = DialMath.nearestIndex(bases, rot)
            val landed = (bases[i] + rot + DialMath.snapDelta(bases[i], rot) + 90f) % 360f
            assertEquals(0f, (landed + 360f) % 360f, 0.001f)
        }
    }

    @Test
    fun nearestPicksTopmost() {
        val bases = listOf(-90f, 30f, 150f)
        assertEquals(0, DialMath.nearestIndex(bases, 0f)) // Money on top
        assertEquals(2, DialMath.nearestIndex(bases, 120f)) // Note rotated to top
        assertEquals(1, DialMath.nearestIndex(bases, -120f)) // Food rotated to top
        assertEquals(0, DialMath.nearestIndex(bases, 720f)) // full turns change nothing
    }
}

// ponytail: one check that the hijacked scanner route is intact — typo here = dead button.
// (Uri.parse can't run on JVM unit tests, so the constant itself is the source of truth.)
class PhonePeTest {
    @Test
    fun scanRouteConstants() {
        assertEquals("com.phonepe.app", PhonePe.PKG)
        assertEquals("phonepe://native?id=scanQR", PhonePe.SCAN_URI)
    }
}
