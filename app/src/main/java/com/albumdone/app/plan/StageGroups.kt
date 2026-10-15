package com.albumdone.app.plan

/**
 * Family groups photographed with the couple: on the reception stage, at the mandap, at any
 * function. Pure Kotlin, unit-tested.
 *
 * Each group is one visit: the same people with the bride and groom, a few minutes apart at most.
 * The photographer usually takes two or three frames of each; the planner already marks the
 * extras as repeats, so a group is shown once, with its best frame.
 */
object StageGroups {

    data class Group(
        /** The best frame of this group. */
        val photoId: Long,
        /** Every frame of this group, best first. */
        val photoIds: List<Long>,
        /** Everyone in it besides the bride and groom. */
        val guests: Set<Long>,
        /** Capture order of the first frame, to list groups as they happened. */
        val order: Int,
    )

    /** Frames of one group this far apart at most. */
    const val SAME_VISIT_MILLIS = 5 * 60_000L
    /** Two frames show the same group when this share of their guests is the same. */
    const val SAME_GUESTS = 0.5

    fun find(analysis: AlbumAnalysis): List<Group> {
        val bride = analysis.tags.brideId ?: return emptyList()
        val groom = analysis.tags.groomId ?: return emptyList()
        val withCouple = analysis.photos
            .filter { bride in it.people && groom in it.people && Issue.UNREADABLE !in it.issues }
            .filter { (it.people - bride - groom).isNotEmpty() }
            .sortedBy { it.order }

        class Open(val frames: MutableList<AssessedPhoto>, val guests: MutableSet<Long>)
        val groups = ArrayList<Open>()
        for (p in withCouple) {
            val guests = p.people - bride - groom
            val last = groups.lastOrNull()
            val prev = last?.frames?.last()
            val close = prev != null && when {
                p.repeatGroup >= 0 && p.repeatGroup == prev.repeatGroup -> true
                p.photo.takenAt > 0 && prev.photo.takenAt > 0 -> p.photo.takenAt - prev.photo.takenAt <= SAME_VISIT_MILLIS
                else -> p.order - prev.order <= 3
            }
            if (last != null && close && overlap(guests, last.guests) >= SAME_GUESTS) {
                last.frames += p
                last.guests += guests
            } else {
                groups += Open(mutableListOf(p), guests.toMutableSet())
            }
        }
        return groups.map { g ->
            val best = g.frames.sortedWith(compareBy<AssessedPhoto> { it.hardIssues.count { i -> i != Issue.DUPLICATE && i != Issue.GROUP_REPEAT } }
                .thenByDescending { it.quality })
            Group(best.first().id, best.map { it.id }, g.guests, g.frames.first().order)
        }
    }

    private fun overlap(a: Set<Long>, b: Set<Long>): Double {
        val union = a.union(b).size
        return if (union == 0) 0.0 else a.intersect(b).size.toDouble() / union
    }
}
