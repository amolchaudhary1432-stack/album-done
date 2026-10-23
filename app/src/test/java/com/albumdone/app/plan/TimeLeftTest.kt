package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeLeftTest {

    @Test
    fun aSlowStartDoesNotMakeTheWaitLookLonger() {
        // 1,000 photos: the first 30 s check only 10 (models loading), then 2 a second.
        val t = TimeLeft()
        var done = 0
        for (s in 0..120 step 2) {
            done = if (s <= 30) s / 3 else 10 + (s - 30) * 2
            t.add(s * 1000L, done)
        }
        val left = t.secondsLeft(1000)!!
        // 190 done, 810 left at 2 a second: about 405 s. The average since the start says 511 s.
        assertEquals(405.0, left.toDouble(), 10.0)
    }

    @Test
    fun saysNothingUntilItHasWatchedLongEnough() {
        val t = TimeLeft()
        t.add(0, 0)
        t.add(5_000, 10)
        assertNull(t.secondsLeft(1000))
        t.add(16_000, 30)
        assertTrue(t.secondsLeft(1000)!! > 0)
    }

    @Test
    fun startsAgainWhenCheckingRestarts() {
        val t = TimeLeft()
        t.add(0, 0); t.add(30_000, 60)
        t.add(1_000, 0) // the album was re-made
        assertNull(t.secondsLeft(1000))
    }
}
