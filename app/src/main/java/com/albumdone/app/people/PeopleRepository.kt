package com.albumdone.app.people

import com.albumdone.app.analysis.FaceEmbedder
import com.albumdone.app.data.AlbumDao
import com.albumdone.app.data.FaceEmbeddingRow
import com.albumdone.app.data.PersonEntity
import com.albumdone.app.data.PersonPriority
import com.albumdone.app.data.PersonSide
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectEntity
import com.albumdone.app.plan.AlbumPlanner
import com.albumdone.app.plan.Box
import com.albumdone.app.plan.EventKind
import com.albumdone.app.plan.EyeState
import com.albumdone.app.plan.Expression
import com.albumdone.app.plan.PeopleTags
import com.albumdone.app.plan.PersonTag
import com.albumdone.app.plan.PlanFace
import com.albumdone.app.plan.PlanPhoto
import com.albumdone.app.plan.Priority
import com.albumdone.app.plan.Side

/** Saves and edits the people of a wedding, and loads everything the planner needs. */
class PeopleRepository(private val dao: AlbumDao) {

    private fun FaceEmbeddingRow.vector() = FaceVector(id, photoId, FaceEmbedder.fromBytes(embedding), quality)

    /** Groups all faces of the wedding into people and guesses the couple. Safe to run again. */
    suspend fun build(projectId: Long) {
        val faces = dao.faceEmbeddings(projectId).map { it.vector() }
        dao.clearPeopleOfFaces(projectId)
        dao.deletePeople(projectId)
        for (group in PeopleClusterer.cluster(faces)) {
            val id = dao.insertPerson(PersonEntity(projectId = projectId))
            group.chunked(500).forEach { dao.setPerson(it, id) }
        }
        refreshStats(projectId)
        detectCouple(projectId)
    }

    suspend fun detectCouple(projectId: Long) {
        val rows = dao.faceRows(projectId)
        val clear = rows.filter { it.right - it.left >= 0.03f }
        val photosOf = clear.filter { it.personId != null }
            .groupBy { it.personId!! }.mapValues { e -> e.value.map { it.photoId }.toSet() }
        val photos = dao.photos(projectId)
        val hints = photos.filter { isBrideEvent(it.folder) }.map { it.id }.toSet()
        // Where each photo was taken decides how much "together" counts (see detectCouple).
        val eventOf = AlbumPlanner.assignEvents(photos.map { PlanPhoto(id = it.id, name = it.name, folder = it.folder) })
        val weight = photos.associate { p ->
            val event = eventOf[p.id] ?: ""
            p.id to when {
                AlbumPlanner.ownFunction(event) -> PeopleClusterer.OWN_FUNCTION_WEIGHT
                AlbumPlanner.eventKind(event) == EventKind.RITUAL || AlbumPlanner.eventKind(event) == EventKind.PARTY -> PeopleClusterer.WEDDING_WEIGHT
                else -> 1.0
            }
        }
        val byPhoto = clear.groupBy { it.photoId }
        val twoShots = byPhoto.filter { it.value.size == 2 }.keys
        val portraits = byPhoto.filter { (_, f) -> f.size == 2 && f.all { it.right - it.left >= 0.08f } }.keys
        val couple = PeopleClusterer.detectCouple(photosOf, hints, { weight[it] ?: 1.0 }, twoShots)
        dao.setCouple(projectId, couple?.first, couple?.second)
        dao.setCoupleSure(projectId, couple != null && PeopleClusterer.coupleSure(photosOf, couple, { weight[it] ?: 1.0 }, portraits))
    }

    /** Photo counts and best face of every person; people left with no photos are removed. */
    suspend fun refreshStats(projectId: Long) {
        val counts = dao.personPhotoCounts(projectId).associate { it.personId to it.photos }
        for (p in dao.people(projectId)) {
            val n = counts[p.id] ?: 0
            if (n == 0 && dao.faceEmbeddingsOf(p.id).isEmpty()) dao.deletePerson(p.id)
            else dao.setPersonStats(p.id, n, dao.bestFaceOf(p.id))
        }
    }

    /** [gone] was the same person as [keep]. */
    suspend fun merge(projectId: Long, keep: Long, gone: Long) {
        if (keep == gone) return
        val keepPerson = dao.person(keep) ?: return
        val gonePerson = dao.person(gone) ?: return
        dao.moveFaces(gone, keep)
        // Keep whatever the couple already told us about either of them.
        dao.updatePerson(
            keepPerson.copy(
                name = keepPerson.name.ifBlank { gonePerson.name },
                priority = if (keepPerson.priority == "NORMAL") gonePerson.priority else keepPerson.priority,
                side = if (keepPerson.side == "UNKNOWN") gonePerson.side else keepPerson.side,
                elder = keepPerson.elder || gonePerson.elder,
            )
        )
        dao.deletePerson(gone)
        val project = dao.project(projectId)
        if (project != null && (project.brideId == gone || project.groomId == gone)) {
            dao.setCouple(projectId, if (project.brideId == gone) keep else project.brideId, if (project.groomId == gone) keep else project.groomId)
        }
        refreshStats(projectId)
    }

    /** One group holds two or more people: separate them. Returns how many groups it became. */
    suspend fun split(projectId: Long, personId: Long): Int {
        val faces = dao.faceEmbeddingsOf(personId).map { it.vector() }
        val parts = PeopleClusterer.split(faces)
        if (parts.size <= 1) return 1
        for (part in parts.drop(1)) {
            val id = dao.insertPerson(PersonEntity(projectId = projectId))
            part.chunked(500).forEach { dao.setPerson(it, id) }
        }
        refreshStats(projectId)
        return parts.size
    }

    /** Looks for more photos of this person among unmatched faces. Returns how many were found. */
    suspend fun findMore(projectId: Long, personId: Long): Int {
        val all = dao.faceEmbeddings(projectId)
        val groupSize = all.groupingBy { it.personId }.eachCount()
        val person = all.filter { it.personId == personId }.map { it.vector() }
        val sizeOfFace = all.associate { it.id to (it.personId?.let { p -> groupSize[p] } ?: 0) }
        val found = PeopleClusterer.findMore(person, all.filter { it.personId != personId }.map { it.vector() }) { sizeOfFace[it] ?: 0 }
        if (found.isEmpty()) return 0
        found.chunked(500).forEach { dao.setPerson(it, personId) }
        refreshStats(projectId)
        return found.size
    }

    /** Everything the planner needs: photos with their faces, and the couple's tags. */
    suspend fun planInput(project: ProjectEntity): Pair<List<PlanPhoto>, PeopleTags> {
        val faces = dao.faceRows(project.id).groupBy { it.photoId }
        val photos = dao.photos(project.id).map { p -> p.toPlan(faces[p.id].orEmpty().map { f ->
            PlanFace(
                personId = f.personId,
                box = Box(f.left, f.top, f.right, f.bottom),
                eyes = enumOr(f.eyes, EyeState.OPEN),
                expression = enumOr(f.expression, Expression.NATURAL),
                smile = f.smile,
                lookingAtCamera = f.looking,
                directionX = f.directionX,
                sharpness = f.sharpness,
                brightness = f.brightness,
                backlit = f.backlit,
                occlusion = f.occlusion,
                cutOff = f.cutOff,
            )
        }) }
        val people = dao.people(project.id)
        val userTags = people.associate { p ->
            p.id to PersonTag(p.id, p.name, enumOr(p.priority, Priority.NORMAL), enumOr(p.side, Side.UNKNOWN), p.elder)
        }
        // The couple's own tags win; the app's guess of the close family fills in everyone else,
        // so the album covers the family even if nobody tags anyone.
        val eventOf = AlbumPlanner.assignEvents(photos)
        val forGuess = photos.map { p ->
            val event = eventOf[p.id] ?: ""
            FamilyGuess.PhotoPeople(
                photoId = p.id,
                event = event,
                familyEvent = AlbumPlanner.eventKind(event) != EventKind.PARTY,
                people = p.faces.filter { it.box.width >= AlbumPlanner.VISIBLE_FACE }.mapNotNull { it.personId }.toSet(),
            )
        }
        val guesses = FamilyGuess.guess(forGuess, project.brideId, project.groomId)
        val userTagged = people.filter {
            it.priority != PersonPriority.NORMAL || it.side != PersonSide.UNKNOWN || it.elder
        }.map { it.id }.toSet()
        val tags = FamilyGuess.mergeWithUserTags(guesses, PeopleTags(project.brideId, project.groomId, userTags), userTagged)
        return photos to tags
    }

    private fun PhotoEntity.toPlan(faces: List<PlanFace>) = PlanPhoto(
        id = id,
        name = name,
        folder = folder,
        takenAt = takenAt,
        width = width,
        height = height,
        sharpness = sharpness,
        peakSharpness = peakSharpness,
        brightness = brightness,
        darkFraction = darkFraction,
        brightFraction = brightFraction,
        hash = hash,
        tilt = tilt,
        faces = faces,
        phones = Box.decodeList(phones),
        foregroundBacks = foregroundBacks,
        peopleArea = peopleArea,
        failed = failed || !processed,
    )

    companion object {
        private val BRIDE_WORDS = listOf("mehendi", "mehndi", "mehandi", "bride", "bridal", "dulhan", "chooda", "chuda")

        fun isBrideEvent(folder: String): Boolean = folder.lowercase().let { f -> BRIDE_WORDS.any { f.contains(it) } }

        inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
