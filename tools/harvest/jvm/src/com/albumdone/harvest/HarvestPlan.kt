package com.albumdone.harvest

import com.albumdone.app.people.FaceVector
import com.albumdone.app.people.PeopleClusterer
import com.albumdone.app.people.PeoplePlan
import com.albumdone.app.plan.Occasion
import com.albumdone.app.plan.PlanDump
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * raw-dump.txt (from to_dump.py) -> plan-input.txt: faces grouped into people, the couple or main
 * people found and the family guessed, exactly as the app does after checking a folder.
 *
 *     gradle run --args="raw-dump.txt plan-input.txt [OCCASION]"
 *     gradle run --args="FOLDER"     every *.raw.txt in FOLDER -> *.plan.txt
 *     gradle run --args="score ..."  albums against a couple's real album (AlbumScore.kt)
 *     gradle run --args="couple PLAN" who the app finds as the couple in a plan dump, found again
 */
fun main(args: Array<String>) {
    if (args[0] == "score") return score(args.drop(1))
    if (args[0] == "learn-data") return learnData(args.drop(1))
    if (args[0] == "harvest-score") return harvestScore(args.drop(1))
    if (args[0] == "couple" && File(args[1]).isDirectory) {
        // Every plan dump in a folder: the couple stored in it (as found when it was made) and now.
        for (f in File(args[1]).listFiles { x -> x.name.endsWith(".plan.txt") }.orEmpty().sortedBy { it.name }) {
            val (photos, tags) = com.albumdone.app.plan.PlanDump.read(f.readText())
            val main = com.albumdone.app.people.PeoplePlan.findMain(tags.occasion, photos)
            val same = tags.brideId == main.first && tags.groomId == main.second
            println("${if (same) "same   " else "CHANGED"} ${f.name}: was ${tags.brideId} ${tags.groomId}, now ${main.first} ${main.second} sure=${main.sure}")
        }
        return
    }
    if (args[0] == "couple") {
        val (photos, tags) = com.albumdone.app.plan.PlanDump.read(File(args[1]).readText())
        val main = com.albumdone.app.people.PeoplePlan.findMain(tags.occasion, photos)
        println("dump: ${tags.brideId} ${tags.groomId} | now: ${main.first} ${main.second} sure=${main.sure}")
        // With a size and an output file: the album made with the couple found now, cover first.
        val size = args.getOrNull(2)?.toIntOrNull() ?: return
        val now = com.albumdone.app.people.PeoplePlan.tags(tags.occasion, photos, main.first, main.second)
        val a = com.albumdone.app.plan.AlbumPlanner.analyze(photos, now)
        val list = com.albumdone.app.plan.AlbumPlanner.shortlist(a, size)
        fun label(id: Long) = a.byId.getValue(id).photo.let { if (it.folder.isEmpty()) it.name else "${it.folder}/${it.name}" }
        File(args[3]).writeText((listOfNotNull(list.coverId) + list.ids.filter { it != list.coverId }).joinToString("") { label(it) + "\n" })
        println("cover: ${list.coverId?.let { label(it) }}, ${list.ids.size} photos")
        return
    }
    val first = File(args[0])
    if (first.isDirectory) {
        for (raw in first.listFiles { f -> f.name.endsWith(".raw.txt") }.orEmpty().sortedBy { it.name }) {
            plan(raw, File(raw.path.removeSuffix(".raw.txt") + ".plan.txt"), null)
        }
    } else {
        plan(first, File(args[1]), args.getOrNull(2))
    }
}

fun plan(rawFile: File, outFile: File, occasionName: String?) {
    val text = rawFile.readText()
    val (photos, rawTags) = PlanDump.read(text)
    val occasion = if (occasionName != null) Occasion.of(occasionName) else rawTags.occasion

    // Face fingerprints: photo id, face number in that photo, quality, 128 numbers.
    val faceIds = HashMap<Long, Pair<Long, Int>>()
    val vectors = ArrayList<FaceVector>()
    for (line in text.lineSequence()) {
        val c = line.split('\t')
        if (c[0] != "EMB") continue
        val bytes = ByteBuffer.wrap(Base64.getDecoder().decode(c[4])).order(ByteOrder.LITTLE_ENDIAN)
        val v = FloatArray(bytes.remaining() / 4) { bytes.getFloat() }
        val id = vectors.size.toLong() + 1
        faceIds[id] = c[1].toLong() to c[2].toInt()
        vectors += FaceVector(id, c[1].toLong(), v, c[3].toFloat())
    }

    val personOf = HashMap<Pair<Long, Int>, Long>()
    PeopleClusterer.cluster(vectors).forEachIndexed { i, group -> for (face in group) personOf[faceIds.getValue(face)] = i + 1L }
    val grouped = photos.map { p -> p.copy(faces = p.faces.mapIndexed { k, f -> f.copy(personId = personOf[p.id to k]) }) }

    val main = PeoplePlan.findMain(occasion, grouped)
    val tags = PeoplePlan.tags(occasion, grouped, main.first, main.second)
    outFile.writeText(PlanDump.write(grouped, tags))
    println("${rawFile.name}: ${photos.size} photos, ${vectors.size} fingerprints, ${personOf.values.toSet().size} people; " +
        "${occasion.name}, main ${main.first} & ${main.second} (${if (main.sure) "sure" else "unsure"})")
}
