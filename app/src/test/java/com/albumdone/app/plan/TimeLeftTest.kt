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
        t.add(0, 1)
        t.add(5_000, 10)
        assertNull(t.secondsLeft(1000))
        t.add(16_000, 30)
        assertTrue(t.secondsLeft(1000)!! > 0)
    }

    @Test
    fun theModelsLoadingIsNotCountedAsPace() {
        // On a Galaxy A15: 0 photos for 20 s while the models load, then 1.5 a second.
        val t = TimeLeft()
        t.add(0, 0); t.add(10_000, 0); t.add(20_000, 0)
        for (s in 22..40 step 2) t.add(s * 1000L, ((s - 20) * 1.5).toInt())
        val left = t.secondsLeft(813)!!
        // 30 done at 40 s, 783 left at 1.5 a second: about 522 s, not the 1,000+ the loading time suggests.
        assertEquals(522.0, left.toDouble(), 40.0)
    }

    @Test
    fun startsAgainWhenCheckingRestarts() {
        val t = TimeLeft()
        t.add(0, 1); t.add(30_000, 60)
        t.add(1_000, 1) // the album was re-made
        assertNull(t.secondsLeft(1000))
    }
}
