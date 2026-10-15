package com.albumdone.app.plan

/**
 * The planner's whole input as plain text (one line per photo, face and tag), so a wedding
 * measured on a phone can be replayed on a computer in seconds: change a rule, re-plan, and score
 * the album against a person's picks without checking the photos again. Test tool only; the app
 * never writes this for a couple's photos.
 */
object PlanDump {

    fun write(photos: List<PlanPhoto>, tags: PeopleTags): String = buildString {
        appendLine(listOf("TAGS", tags.brideId ?: "", tags.groomId ?: "").joinToString("\t"))
        for (t in tags.tags.values) {
            appendLine(listOf("TAG", t.id, clean(t.name), t.priority, t.side, t.elder, t.guessed).joinToString("\t"))
        }
        for (p in photos) {
            appendLine(listOf(
                "PHOTO", p.id, clean(p.name), clean(p.folder), p.takenAt, p.width, p.height, p.sharpness, p.peakSharpness,
                p.brightness, p.darkFraction, p.brightFraction, p.hash, p.tilt, p.foregroundBacks, p.peopleArea, p.failed,
                Box.encodeList(p.phones),
            ).joinToString("\t"))
            for (f in p.faces) {
                appendLine(listOf(
                    "FACE", p.id, f.personId ?: "", f.box.left, f.box.top, f.box.right, f.box.bottom, f.eyes, f.expression,
                    f.smile, f.lookingAtCamera, f.directionX, f.sharpness, f.brightness, f.backlit, f.occlusion, f.cutOff,
                ).joinToString("\t"))
            }
        }
    }

    fun read(text: String): Pair<List<PlanPhoto>, PeopleTags> {
        var bride: Long? = null
        var groom: Long? = null
        val tags = LinkedHashMap<Long, PersonTag>()
        val photos = ArrayList<PlanPhoto>()
        val faces = HashMap<Long, MutableList<PlanFace>>()
        for (line in text.lineSequence()) {
            val c = line.split('\t')
            when (c[0]) {
                "TAGS" -> { bride = c[1].toLongOrNull(); groom = c[2].toLongOrNull() }
                "TAG" -> tags[c[1].toLong()] = PersonTag(
                    c[1].toLong(), c[2], Priority.valueOf(c[3]), Side.valueOf(c[4]), c[5].toBoolean(), c[6].toBoolean(),
                )
                "PHOTO" -> photos += PlanPhoto(
                    id = c[1].toLong(), name = c[2], folder = c[3], takenAt = c[4].toLong(), width = c[5].toInt(),
                    height = c[6].toInt(), sharpness = c[7].toDouble(), peakSharpness = c[8].toDouble(),
                    brightness = c[9].toDouble(), darkFraction = c[10].toDouble(), brightFraction = c[11].toDouble(),
                    hash = c[12].toLong(), tilt = c[13].toFloat(), foregroundBacks = c[14].toInt(),
                    peopleArea = c[15].toFloat(), failed = c[16].toBoolean(), phones = Box.decodeList(c.getOrElse(17) { "" }),
                )
                "FACE" -> faces.getOrPut(c[1].toLong()) { mutableListOf() } += PlanFace(
                    personId = c[2].toLongOrNull(), box = Box(c[3].toFloat(), c[4].toFloat(), c[5].toFloat(), c[6].toFloat()),
                    eyes = EyeState.valueOf(c[7]), expression = Expression.valueOf(c[8]), smile = c[9].toFloat(),
                    lookingAtCamera = c[10].toBoolean(), directionX = c[11].toFloat(), sharpness = c[12].toDouble(),
                    brightness = c[13].toDouble(), backlit = c[14].toBoolean(), occlusion = c[15].toFloat(), cutOff = c[16].toFloat(),
                )
            }
        }
        return photos.map { it.copy(faces = faces[it.id].orEmpty()) } to PeopleTags(bride, groom, tags)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
