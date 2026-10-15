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
 *     gradle run --args="raw-dump.txt plan-input.txt WEDDING"
 */
fun main(args: Array<String>) {
    val text = File(args[0]).readText()
    val occasion = Occasion.of(args.getOrNull(2))
    val (photos, _) = PlanDump.read(text)

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
    File(args[1]).writeText(PlanDump.write(grouped, tags))
    println("${photos.size} photos, ${vectors.size} fingerprints, ${personOf.values.toSet().size} people; " +
        "main ${main.first} & ${main.second} (${if (main.sure) "sure" else "unsure"})")
}
