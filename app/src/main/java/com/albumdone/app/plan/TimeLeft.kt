package com.albumdone.app.plan

/**
 * "About N min left" on the progress screen, from how fast photos were checked in the last
 * minute or so. The average since the start is misleading: the first photos are slow while the
 * models load and warm up (on a real Galaxy A15 the start said 58 min for a run that took 9).
 */
class TimeLeft(private val window: Long = 45_000, private val minWindow: Long = 15_000) {

    private val samples = ArrayDeque<Pair<Long, Int>>()

    /** The worker's working time so far (ms) and how many photos are checked. */
    fun add(activeMillis: Long, done: Int) {
        val last = samples.lastOrNull()
        if (last != null && (activeMillis <= last.first || done < last.second)) {
            // Checking restarted (the phone was restarted, or the album was re-made): start again.
            if (activeMillis < last.first || done < last.second) samples.clear() else return
        }
        samples.addLast(activeMillis to done)
        while (samples.size > 2 && activeMillis - samples[1].first >= window) samples.removeFirst()
    }

    /** Seconds left, or null while there is too little to go on. */
    fun secondsLeft(total: Int): Long? {
        if (samples.size < 2) return null
        val (t0, d0) = samples.first()
        val (t1, d1) = samples.last()
        if (t1 - t0 < minWindow || d1 <= d0) return null
        val perSecond = (d1 - d0) * 1000.0 / (t1 - t0)
        return ((total - d1).coerceAtLeast(0) / perSecond).toLong()
    }
}
