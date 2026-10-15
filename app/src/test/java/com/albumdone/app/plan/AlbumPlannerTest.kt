package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumPlannerTest {

    private var nextId = 1L
    private val start = 1_700_000_000_000L
    private val minute = 60_000L
    private val BRIDE = 1L
    private val GROOM = 2L
    private val couple = PeopleTags(brideId = BRIDE, groomId = GROOM)

    /** Well-spread 64-bit hashes so unrelated photos never look alike. */
    private fun hashOf(id: Long): Long {
        var z = id * -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun face(
        person: Long? = null,
        x: Float = 0.3f,
        w: Float = 0.1f,
        eyes: EyeState = EyeState.OPEN,
        expr: Expression = Expression.SMILE,
        looking: Boolean = true,
        dir: Float = 0f,
        sharp: Double = 100.0,
        bright: Double = 130.0,
        backlit: Boolean = false,
        occlusion: Float = 0f,
        cut: Float = 0f,
    ) = PlanFace(
        personId = person, box = Box(x, 0.2f, x + w, 0.2f + w * 1.5f), eyes = eyes, expression = expr,
        smile = if (expr == Expression.SMILE || expr == Expression.LAUGH) 0.7f else 0.1f,
        lookingAtCamera = looking, directionX = dir, sharpness = sharp, brightness = bright,
        backlit = backlit, occlusion = occlusion, cutOff = cut,
    )

    private fun photo(
        folder: String = "Reception",
        t: Long = 0,
        faces: List<PlanFace> = emptyList(),
        hash: Long? = null,
        peak: Double = 100.0,
        brightness: Double = 128.0,
        dark: Double = 0.0,
        blown: Double = 0.0,
        tilt: Float = 0f,
        phones: List<Box> = emptyList(),
        backs: Int = 0,
        failed: Boolean = false,
        portrait: Boolean = false,
        name: String? = null,
    ): PlanPhoto {
        val id = nextId++
        return PlanPhoto(
            id = id, name = name ?: "IMG_%04d.jpg".format(id), folder = folder, takenAt = t,
            width = if (portrait) 1000 else 1500, height = if (portrait) 1500 else 1000,
            sharpness = 100.0, peakSharpness = peak, brightness = brightness, darkFraction = dark,
            brightFraction = blown, hash = hash ?: hashOf(id), tilt = tilt, faces = faces, phones = phones,
            foregroundBacks = backs, failed = failed,
        )
    }

    private fun couplePhoto(folder: String = "Reception", t: Long = 0, w: Float = 0.1f, extra: List<PlanFace> = emptyList(),
                            brideFace: PlanFace? = null, groomFace: PlanFace? = null) =
        photo(folder, t, listOf(brideFace ?: face(BRIDE, x = 0.3f, w = w), groomFace ?: face(GROOM, x = 0.5f, w = w)) + extra)

    private fun AlbumAnalysis.issues(p: PlanPhoto) = byId.getValue(p.id).issues
    private fun AlbumAnalysis.q(p: PlanPhoto) = byId.getValue(p.id).quality

    // =====================================================================================
    // Events
    // =====================================================================================

    @Test
    fun foldersAreEventsInThePhotographersNumberedOrder() {
        val photos = listOf(
            photo("Wedding Photos/03 Wedding", start), photo("Wedding Photos/01 Haldi", start + 86_400_000L),
            photo("Wedding Photos/02 Mehendi", start + 2 * 86_400_000L), photo("Wedding Photos/03 Wedding/Candid", start),
        )
        val a = AlbumPlanner.analyze(photos)
        assertEquals(listOf("01 Haldi", "02 Mehendi", "03 Wedding"), a.events)
        assertEquals("03 Wedding", a.byId.getValue(photos[3].id).event)
    }

    @Test
    fun unnumberedFoldersFollowWhenEachEventStarted() {
        val photos = listOf(
            photo("Reception", start + 3 * 86_400_000L), photo("Haldi", start),
            photo("Wedding", start + 2 * 86_400_000L), photo("Mehendi", start + 86_400_000L),
        )
        assertEquals(listOf("Haldi", "Mehendi", "Wedding", "Reception"), AlbumPlanner.analyze(photos).events)
    }

    @Test
    fun oneFolderIsOneEventEvenWithLongGaps() {
        val photos = listOf(photo("Wedding", start), photo("Wedding", start + 5 * 60 * minute), photo("Wedding", 0))
        assertEquals(listOf("Wedding"), AlbumPlanner.analyze(photos).events)
        assertEquals(listOf(AlbumPlanner.ALL_PHOTOS), AlbumPlanner.analyze(listOf(photo(""), photo(""))).events)
    }

    @Test
    fun eventKindsFromFolderNames() {
        assertEquals(EventKind.RITUAL, AlbumPlanner.eventKind("03 Wedding - Pheras"))
        assertEquals(EventKind.VIDAAI, AlbumPlanner.eventKind("Vidaai"))
        assertEquals(EventKind.PARTY, AlbumPlanner.eventKind("Sangeet Night"))
        assertEquals(EventKind.PREP, AlbumPlanner.eventKind("Mehndi"))
        assertEquals(EventKind.OTHER, AlbumPlanner.eventKind("Couple shoot Goa"))
    }

    // =====================================================================================
    // Per-face checks
    // =====================================================================================

    @Test
    fun eyesClosedOnAnyClearFace() {
        val closed = photo(faces = listOf(face(x = 0.1f), face(x = 0.5f, eyes = EyeState.CLOSED)))
        val tinyBackground = photo(faces = listOf(face(x = 0.1f), face(x = 0.5f, w = 0.04f, eyes = EyeState.CLOSED)))
        val a = AlbumPlanner.analyze(listOf(closed, tinyBackground))
        assertTrue(Issue.EYES_CLOSED in a.issues(closed))
        assertFalse(a.byId.getValue(closed.id).usable)
        assertTrue(a.byId.getValue(tinyBackground.id).usable)
    }

    @Test
    fun halfBlinkAndMidTalkAreFlaggedButNotRejected() {
        val half = photo(faces = listOf(face(eyes = EyeState.HALF)))
        val talk = photo(faces = listOf(face(expr = Expression.MID_TALK)))
        val good = photo(faces = listOf(face()))
        val a = AlbumPlanner.analyze(listOf(half, talk, good))
        assertTrue(Issue.HALF_BLINK in a.issues(half))
        assertTrue(Issue.MID_TALK in a.issues(talk))
        assertTrue(a.byId.getValue(half.id).usable && a.byId.getValue(talk.id).usable)
        assertTrue(a.q(good) > a.q(half) && a.q(good) > a.q(talk))
    }

    @Test
    fun notLookingAtCameraInAPosedGroup() {
        val group = photo(faces = listOf(face(x = 0.1f), face(x = 0.3f), face(x = 0.5f, looking = false)))
        val a = AlbumPlanner.analyze(listOf(group))
        assertTrue(Issue.NOT_LOOKING in a.issues(group))
    }

    @Test
    fun faceSharpnessIsMeasuredOnTheFaceNotTheBackground() {
        val normal = List(5) { photo(faces = listOf(face(sharp = 100.0)), peak = 100.0) }
        val bokeh = photo(faces = listOf(face(sharp = 100.0)), peak = 5.0)          // soft background, sharp face
        val missedFocus = photo(faces = listOf(face(sharp = 20.0)), peak = 300.0)  // sharp background, soft face
        val softDetail = photo(peak = 10.0)                                        // no faces, nothing sharp
        val a = AlbumPlanner.analyze(normal + bokeh + missedFocus + softDetail)
        assertFalse(Issue.BLURRY in a.issues(bokeh))
        assertTrue(Issue.BLURRY in a.issues(missedFocus))
        assertTrue(Issue.BLURRY in a.issues(softDetail))
    }

    @Test
    fun groupPhotosAreNotJudgedAgainstCloseUps() {
        // Found on the phone test: small faces measure less sharp than big ones even in focus,
        // which marked group photos down against close-up portraits.
        val portraits = List(10) { photo(faces = listOf(face(x = 0.35f, w = 0.3f, sharp = 1300.0))) }
        val groups = List(6) { photo(faces = List(4) { k -> face(x = 0.05f + k * 0.22f, w = 0.07f, sharp = 300.0) }) }
        val blurredGroup = photo(faces = List(4) { k -> face(x = 0.05f + k * 0.22f, w = 0.07f, sharp = 60.0) })
        val a = AlbumPlanner.analyze(portraits + groups + blurredGroup)
        for (g in groups) assertFalse(Issue.BLURRY in a.issues(g))
        assertTrue(a.q(groups[0]) >= 0.9 * a.q(portraits[0]))
        assertTrue(Issue.BLURRY in a.issues(blurredGroup))
    }

    @Test
    fun darkerSkinIsNotAShadow() {
        val faces = List(6) { photo(faces = listOf(face(bright = 140.0))) }
        val darkerSkin = photo(faces = listOf(face(x = 0.1f, bright = 140.0), face(x = 0.5f, bright = 60.0)))
        val a = AlbumPlanner.analyze(faces + darkerSkin)
        assertFalse(Issue.FACE_DARK in a.issues(darkerSkin))
    }

    @Test
    fun faceExposureBacklitOrShadowed() {
        val backlit = photo(faces = listOf(face(backlit = true)))
        val shadow = photo(faces = listOf(face(x = 0.1f), face(x = 0.5f, bright = 20.0)))
        val fine = photo(faces = listOf(face()))
        val a = AlbumPlanner.analyze(listOf(backlit, shadow, fine))
        assertTrue(Issue.FACE_DARK in a.issues(backlit))
        assertTrue(Issue.FACE_DARK in a.issues(shadow))
        assertFalse(Issue.FACE_DARK in a.issues(fine))
    }

    @Test
    fun faceBlockedByAnotherPersonOrPhone() {
        val behind = photo(faces = listOf(face(x = 0.3f, w = 0.1f), face(x = 0.32f, w = 0.15f)))
        val phone = photo(faces = listOf(face()), phones = listOf(Box(0.32f, 0.25f, 0.38f, 0.33f)))
        val hand = photo(faces = listOf(face(occlusion = 0.8f)))
        val a = AlbumPlanner.analyze(listOf(behind, phone, hand))
        assertTrue(Issue.FACE_BLOCKED in a.issues(behind))
        assertTrue(Issue.FACE_BLOCKED in a.issues(phone))
        assertTrue(Issue.FACE_PARTLY_HIDDEN in a.issues(hand))
        assertTrue(a.byId.getValue(hand.id).usable)
    }

    @Test
    fun faceCutOffByTheEdge() {
        val cut = photo(faces = listOf(face(x = 0.95f, cut = 0.4f)))
        val a = AlbumPlanner.analyze(listOf(cut))
        assertTrue(Issue.FACE_CUT_OFF in a.issues(cut))
        assertFalse(a.byId.getValue(cut.id).usable)
    }

    // =====================================================================================
    // Per-photo checks
    // =====================================================================================

    @Test
    fun darkAndBrightPhotos() {
        val photos = listOf(photo("Haldi", brightness = 130.0), photo("Haldi", brightness = 35.0), photo("Haldi", brightness = 130.0, dark = 0.7),
            photo("Haldi", brightness = 230.0), photo("Haldi", brightness = 150.0, blown = 0.55))
        val a = AlbumPlanner.analyze(photos)
        assertTrue(a.byId.getValue(photos[0].id).usable)
        assertTrue(Issue.TOO_DARK in a.issues(photos[1]))
        assertTrue(Issue.TOO_DARK in a.issues(photos[2]))
        assertTrue(Issue.TOO_BRIGHT in a.issues(photos[3]))
        assertTrue(Issue.TOO_BRIGHT in a.issues(photos[4]))
        assertEquals(4, a.darkOrBrightCount)
    }

    @Test
    fun stageLightingIsJudgedAgainstItsOwnEvent() {
        val reception = List(5) { photo("Reception", brightness = 45.0, dark = 0.45) }
        val dimReception = photo("Reception", brightness = 35.0, dark = 0.6)
        val haldi = List(5) { photo("Haldi", brightness = 130.0) }
        val dimHaldi = photo("Haldi", brightness = 35.0)
        val a = AlbumPlanner.analyze(reception + dimReception + haldi + dimHaldi)
        assertTrue(a.byId.getValue(dimReception.id).usable)
        assertTrue(Issue.TOO_DARK in a.issues(dimHaldi))
    }

    @Test
    fun slightlyCrookedPhotosButNotCreativeAngles() {
        val crooked = photo(tilt = 5f)
        val creative = photo(tilt = 25f)
        val level = photo(tilt = 1f)
        val a = AlbumPlanner.analyze(listOf(crooked, creative, level))
        assertTrue(Issue.TILTED in a.issues(crooked))
        assertTrue(a.byId.getValue(crooked.id).usable)
        assertFalse(Issue.TILTED in a.issues(creative))
        assertFalse(Issue.TILTED in a.issues(level))
    }

    @Test
    fun burstKeepsTheBestShot() {
        val h = 0x0F0F_0F0F_0F0F_0F0FL
        val a1 = photo(t = start, hash = h, faces = listOf(face(expr = Expression.NATURAL)))
        val a2 = photo(t = start + 1_000, hash = h xor 0b111, faces = listOf(face(expr = Expression.SMILE)))
        val a3 = photo(t = start + 2_500, hash = h xor 0b1, faces = listOf(face(eyes = EyeState.HALF)))
        val later = photo(t = start + 8_000, hash = h.inv())
        val a = AlbumPlanner.analyze(listOf(a1, a2, a3, later))
        assertTrue(a.byId.getValue(a2.id).usable)
        assertEquals(3, a.byId.getValue(a2.id).similarShots)
        assertTrue(Issue.DUPLICATE in a.issues(a1))
        assertTrue(Issue.DUPLICATE in a.issues(a3))
        assertTrue(a.byId.getValue(later.id).usable)
    }

    @Test
    fun nearIdenticalRepeatsAMinuteApartKeepTheBest() {
        val h = 0x1234_5678_9ABC_DEF0L
        val first = photo(t = start, hash = h, faces = listOf(face(expr = Expression.NATURAL)))
        val between = photo(t = start + 20_000)
        val again = photo(t = start + 40_000, hash = h xor 0b11, faces = listOf(face()))
        val a = AlbumPlanner.analyze(listOf(first, between, again))
        assertTrue(Issue.DUPLICATE in a.issues(first))
        assertTrue(a.byId.getValue(again.id).usable)
        assertTrue(a.byId.getValue(between.id).usable)
    }

    @Test
    fun burstByFileNameWhenTimeUnknown() {
        val p1 = photo(hash = 7L, name = "DSC_0001.jpg")
        val p2 = photo(hash = 7L, name = "DSC_0002.jpg")
        val p3 = photo(hash = 7L.inv(), name = "DSC_0003.jpg")
        val a = AlbumPlanner.analyze(listOf(p3, p2, p1))
        assertEquals(1, a.count(Issue.DUPLICATE))
        assertTrue(a.byId.getValue(p3.id).usable)
    }

    @Test
    fun portraitOrLandscape() {
        assertTrue(photo(portrait = true).portrait)
        assertFalse(photo().portrait)
    }

    @Test
    fun strangersPhonesAndBacksOfGuests() {
        val couplePhotos = List(5) { couplePhoto(t = start + it * 20 * minute) }
        val stranger = couplePhoto(t = start + 200 * minute, extra = listOf(face(99, x = 0.7f, looking = false, sharp = 20.0, eyes = EyeState.CLOSED)))
        val phone = couplePhoto(t = start + 220 * minute).copy(phones = listOf(Box(0.8f, 0.7f, 0.86f, 0.8f)))
        val back = couplePhoto(t = start + 240 * minute).copy(foregroundBacks = 1)
        val a = AlbumPlanner.analyze(couplePhotos + stranger + phone + back, couple)
        assertTrue(Issue.STRANGER in a.issues(stranger))
        // The passer-by's closed eyes are not held against the couple's photo.
        assertFalse(Issue.EYES_CLOSED in a.issues(stranger))
        assertTrue(Issue.PHONE_IN_SHOT in a.issues(phone))
        assertTrue(Issue.BACK_OF_GUEST in a.issues(back))
        assertFalse(a.byId.getValue(back.id).usable)
        assertTrue(a.q(couplePhotos[0]) > a.q(stranger))
        assertTrue(a.q(couplePhotos[0]) > a.q(phone))
    }

    // =====================================================================================
    // Group photos
    // =====================================================================================

    @Test
    fun groupIsAsGoodAsItsWorstFace() {
        val allGood = photo(faces = List(8) { face(x = 0.02f + it * 0.12f, w = 0.07f) })
        val oneBlink = photo(faces = List(8) { face(x = 0.02f + it * 0.12f, w = 0.07f, eyes = if (it == 5) EyeState.HALF else EyeState.OPEN) })
        val a = AlbumPlanner.analyze(listOf(allGood, oneBlink))
        assertTrue(a.q(oneBlink) < 0.7 * a.q(allGood))
        assertTrue(a.byId.getValue(allGood.id).allEyesOpenAndLooking)
    }

    @Test
    fun sameFamilyOnStageCollapsesToTheBestPhoto() {
        fun family(t: Long, blinker: Int?) = photo("Reception", t, listOf(
            face(BRIDE, x = 0.05f, w = 0.07f), face(GROOM, x = 0.2f, w = 0.07f),
            face(10, x = 0.35f, w = 0.07f, eyes = if (blinker == 0) EyeState.HALF else EyeState.OPEN),
            face(11, x = 0.5f, w = 0.07f, looking = blinker != 1), face(12, x = 0.65f, w = 0.07f),
        ))
        val f1 = family(start, 0)
        val f2 = family(start + 2 * minute, null)
        val f3 = family(start + 4 * minute, 1)
        val otherFamily = photo("Reception", start + 6 * minute, listOf(
            face(BRIDE, x = 0.05f, w = 0.07f), face(GROOM, x = 0.2f, w = 0.07f),
            face(20, x = 0.35f, w = 0.07f), face(21, x = 0.5f, w = 0.07f), face(22, x = 0.65f, w = 0.07f),
        ))
        val a = AlbumPlanner.analyze(listOf(f1, f2, f3, otherFamily), couple)
        assertTrue(a.byId.getValue(f2.id).usable)
        assertTrue(Issue.GROUP_REPEAT in a.issues(f1))
        assertTrue(Issue.GROUP_REPEAT in a.issues(f3))
        assertTrue(a.byId.getValue(otherFamily.id).usable)
        assertEquals(ShotType.GROUP, a.byId.getValue(f2.id).shot)
    }

    // =====================================================================================
    // Context exceptions
    // =====================================================================================

    @Test
    fun noSmileNeededDuringRituals() {
        fun serious(folder: String) = photo(folder, faces = listOf(
            face(x = 0.2f, expr = Expression.NATURAL, looking = false), face(x = 0.5f, expr = Expression.NATURAL, looking = false),
        ))
        val pheras = serious("Wedding Pheras")
        val party = serious("Reception")
        val q1 = AlbumPlanner.analyze(listOf(pheras)).q(pheras)
        val q2 = AlbumPlanner.analyze(listOf(party)).q(party)
        assertTrue(q1 > q2)
        assertTrue(AlbumPlanner.analyze(listOf(pheras)).byId.getValue(pheras.id).ritual)
        // Looking down at the fire is natural during rituals.
        val down = photo("Wedding", faces = listOf(face(eyes = EyeState.LOOKING_DOWN, expr = Expression.NATURAL, looking = false)))
        assertTrue(AlbumPlanner.analyze(listOf(down)).byId.getValue(down.id).usable)
    }

    @Test
    fun lookingAwayIsFineWhenPeopleInteract() {
        val talking = photo(faces = listOf(
            face(x = 0.1f), face(x = 0.25f),
            face(x = 0.45f, looking = false, dir = 0.6f), face(x = 0.65f, looking = false, dir = -0.6f),
        ))
        val ignoring = photo(faces = listOf(face(x = 0.1f), face(x = 0.25f), face(x = 0.45f), face(x = 0.65f, looking = false)))
        val a = AlbumPlanner.analyze(listOf(talking, ignoring))
        assertFalse(Issue.NOT_LOOKING in a.issues(talking))
        assertTrue(a.byId.getValue(talking.id).interaction)
        assertTrue(Issue.NOT_LOOKING in a.issues(ignoring))
    }

    @Test
    fun tearsAreNeverAFlaw() {
        val vidaai = photo("Vidaai", faces = listOf(face(eyes = EyeState.CLOSED, expr = Expression.EMOTIONAL)))
        val crying = photo("Reception", faces = listOf(face(eyes = EyeState.HALF, expr = Expression.EMOTIONAL)))
        val blink = photo("Reception", faces = listOf(face(eyes = EyeState.CLOSED, expr = Expression.NATURAL)))
        val a = AlbumPlanner.analyze(listOf(vidaai, crying, blink))
        assertTrue(a.byId.getValue(vidaai.id).usable)
        assertTrue(a.byId.getValue(vidaai.id).emotional)
        assertTrue(a.issues(crying).isEmpty())
        assertTrue(Issue.EYES_CLOSED in a.issues(blink))
    }

    @Test
    fun laughingWithEyesShutInACandidIsFine() {
        val laugh = photo(faces = listOf(face(x = 0.2f, eyes = EyeState.CLOSED, expr = Expression.LAUGH), face(x = 0.5f, expr = Expression.LAUGH)))
        val a = AlbumPlanner.analyze(listOf(laugh))
        assertFalse(Issue.EYES_CLOSED in a.issues(laugh))
    }

    // =====================================================================================
    // Couple
    // =====================================================================================

    @Test
    fun coverIsTheBestCouplePortrait() {
        val close = couplePhoto(t = start, w = 0.15f)
        val full = couplePhoto(t = start + 20 * minute, w = 0.07f)
        val blink = couplePhoto(t = start + 40 * minute, w = 0.15f, brideFace = face(BRIDE, x = 0.3f, w = 0.15f, eyes = EyeState.HALF))
        val guests = List(10) { photo(t = start + (60 + it * 20) * minute, faces = listOf(face(100L + it))) }
        val a = AlbumPlanner.analyze(listOf(close, full, blink) + guests, couple)
        assertEquals(ShotType.COUPLE_CLOSE, a.byId.getValue(close.id).shot)
        assertEquals(ShotType.COUPLE_FULL, a.byId.getValue(full.id).shot)
        val s = AlbumPlanner.shortlist(a, 5)
        assertEquals(close.id, s.coverId)
        assertTrue(s.reasons.getValue(close.id).first().startsWith("Cover"))
    }

    @Test
    fun soloBrideAndGroomPortraitsAreIncluded() {
        val couples = List(12) { couplePhoto(t = start + it * 20 * minute) }
        val brideSolo = photo(t = start + 300 * minute, faces = listOf(face(BRIDE, expr = Expression.NATURAL)))
        val groomSolo = photo(t = start + 320 * minute, faces = listOf(face(GROOM, expr = Expression.NATURAL)))
        val a = AlbumPlanner.analyze(couples + brideSolo + groomSolo, couple)
        assertEquals(ShotType.BRIDE_SOLO, a.byId.getValue(brideSolo.id).shot)
        for (n in listOf(3, 10)) {
            val s = AlbumPlanner.shortlist(a, n)
            assertTrue(brideSolo.id in s.ids)
            assertTrue(groomSolo.id in s.ids)
        }
    }

    @Test
    fun coupleLookingAtEachOtherIsPreferred() {
        val posed = List(12) { couplePhoto(t = start + it * 20 * minute) }
        val candid = couplePhoto(
            t = start + 300 * minute,
            brideFace = face(BRIDE, x = 0.3f, looking = false, dir = 0.7f, expr = Expression.NATURAL),
            groomFace = face(GROOM, x = 0.5f, looking = false, dir = -0.7f, expr = Expression.NATURAL),
        )
        val a = AlbumPlanner.analyze(posed + candid, couple)
        assertTrue(a.byId.getValue(candid.id).coupleLookingAtEachOther)
        val s = AlbumPlanner.shortlist(a, 9)
        assertTrue(candid.id in s.ids)
        assertTrue(s.reasons.getValue(candid.id).any { it.contains("looking at each other") })
    }

    @Test
    fun theBridesFaceMattersMost() {
        val brideBlinks = couplePhoto(brideFace = face(BRIDE, x = 0.3f, eyes = EyeState.HALF))
        val groomBlinks = couplePhoto(groomFace = face(GROOM, x = 0.5f, eyes = EyeState.HALF))
        val a = AlbumPlanner.analyze(listOf(brideBlinks, groomBlinks), couple)
        assertTrue(a.q(brideBlinks) < a.q(groomBlinks))
    }

    // =====================================================================================
    // People coverage
    // =====================================================================================

    private fun tags(vararg t: PersonTag) = PeopleTags(BRIDE, GROOM, t.associateBy { it.id })

    @Test
    fun mustPeopleGetFivePhotosWithTheCoupleEvenIfOthersLookBetter() {
        val aai = 10L
        val withAai = List(8) { photo(t = start + it * 20 * minute, faces = listOf(face(BRIDE, x = 0.3f), face(aai, x = 0.5f, expr = Expression.NATURAL))) }
        val better = List(30) { couplePhoto(t = start + (200 + it * 20) * minute) }
        val a = AlbumPlanner.analyze(withAai + better, tags(PersonTag(aai, "Aai", Priority.MUST, Side.BRIDE)))
        val s = AlbumPlanner.shortlist(a, 12)
        assertTrue(s.ids.count { aai in a.byId.getValue(it).people } >= 5)
        val cov = s.coverage.single { it.personId == aai }
        assertEquals(5, cov.target)
        assertNull(cov.warning)
        assertTrue(s.reasons.values.flatten().any { it.contains("Aai (Must)") })
    }

    @Test
    fun importantPeopleGetAtLeastOneOrTwo() {
        val mama = 11L
        val withMama = List(3) { photo(t = start + it * 20 * minute, faces = listOf(face(GROOM, x = 0.3f), face(mama, x = 0.5f, expr = Expression.NATURAL))) }
        val better = List(30) { couplePhoto(t = start + (200 + it * 20) * minute) }
        val a = AlbumPlanner.analyze(withMama + better, tags(PersonTag(mama, "Mama", Priority.IMPORTANT, Side.GROOM)))
        val s = AlbumPlanner.shortlist(a, 10)
        assertTrue(s.ids.count { mama in a.byId.getValue(it).people } >= 2)
    }

    @Test
    fun eldersComeFirstWhenSpaceIsTight() {
        val dadi = 30L; val cousin = 31L
        val withDadi = List(5) { photo(t = start + it * 20 * minute, faces = listOf(face(BRIDE, x = 0.3f), face(dadi, x = 0.5f))) }
        val withCousin = List(5) { photo(t = start + (200 + it * 20) * minute, faces = listOf(face(BRIDE, x = 0.3f), face(cousin, x = 0.5f))) }
        val a = AlbumPlanner.analyze(withDadi + withCousin, tags(
            PersonTag(dadi, "Dadi", Priority.MUST, elder = true), PersonTag(cousin, "Cousin", Priority.MUST),
        ))
        val s = AlbumPlanner.shortlist(a, 4)
        val dadiCount = s.ids.count { dadi in a.byId.getValue(it).people }
        val cousinCount = s.ids.count { cousin in a.byId.getValue(it).people }
        assertTrue(dadiCount > cousinCount)
        assertEquals("Dadi", s.coverage.first().name) // elders listed first
    }

    @Test
    fun bothSidesOfTheFamilyAreBalanced() {
        val brideSide = (40L..49L).map { PersonTag(it, "B$it", Priority.NORMAL, Side.BRIDE) }
        val groomSide = (50L..59L).map { PersonTag(it, "G$it", Priority.NORMAL, Side.GROOM) }
        val photos = brideSide.mapIndexed { i, t -> photo(t = start + i * 20 * minute, faces = listOf(face(BRIDE, x = 0.3f), face(t.id, x = 0.5f, sharp = 105.0))) } +
            groomSide.mapIndexed { i, t -> photo(t = start + (i * 20 + 10) * minute, faces = listOf(face(GROOM, x = 0.3f), face(t.id, x = 0.5f))) }
        val a = AlbumPlanner.analyze(photos, tags(*(brideSide + groomSide).toTypedArray()))
        val s = AlbumPlanner.shortlist(a, 10)
        val groomCount = s.ids.count { id -> a.byId.getValue(id).people.any { it in 50L..59L } }
        assertTrue("groom side got $groomCount", groomCount >= 3)
    }

    @Test
    fun nobodyIsOverRepresented() {
        val chachu = 60L
        val withChachu = List(20) { photo(t = start + it * 20 * minute, faces = listOf(face(BRIDE, x = 0.3f), face(GROOM, x = 0.5f), face(chachu, x = 0.7f, sharp = 140.0))) }
        val plain = List(40) { couplePhoto(t = start + (500 + it * 20) * minute, extra = listOf(face(200L + it, x = 0.7f, expr = Expression.NATURAL))) }
        val a = AlbumPlanner.analyze(withChachu + plain, couple)
        val s = AlbumPlanner.shortlist(a, 30)
        val count = s.ids.count { chachu in a.byId.getValue(it).people }
        assertTrue("chachu in $count photos", count <= AlbumPlanner.cap(PersonTag(chachu), 30) + 1)
    }

    @Test
    fun someoneWithOnlyPoorPhotosGetsTheirBestOneAndAWarning() {
        val nani = 70L
        val good = List(6) { couplePhoto(t = start + it * 20 * minute) }
        val blurry = photo(t = start + 300 * minute, faces = listOf(face(BRIDE, x = 0.3f, sharp = 10.0), face(nani, x = 0.5f, sharp = 10.0)))
        val worse = photo(t = start + 320 * minute, faces = listOf(face(BRIDE, x = 0.3f, sharp = 10.0), face(nani, x = 0.5f, sharp = 10.0, eyes = EyeState.CLOSED)))
        val a = AlbumPlanner.analyze(good + blurry + worse, tags(PersonTag(nani, "Nani", Priority.MUST, Side.BRIDE, elder = true)))
        assertFalse(a.byId.getValue(blurry.id).usable)
        val s = AlbumPlanner.shortlist(a, 5)
        assertTrue(blurry.id in s.ids)
        assertFalse(worse.id in s.ids)
        val cov = s.coverage.single()
        assertTrue(cov.warning!!.contains("Only poor photos"))
    }

    @Test
    fun coverageWarnsBelowTheMinimum() {
        val bua = 71L
        val photos = List(2) { photo(t = start + it * 20 * minute, faces = listOf(face(GROOM, x = 0.3f), face(bua, x = 0.5f))) } +
            List(10) { couplePhoto(t = start + (100 + it * 20) * minute) }
        val a = AlbumPlanner.analyze(photos, tags(PersonTag(bua, "Bua", Priority.MUST, Side.GROOM)))
        val s = AlbumPlanner.shortlist(a, 8)
        val cov = s.coverage.single()
        assertEquals(2, cov.picked)
        assertEquals(2, cov.goodAvailable)
        assertTrue(cov.warning!!.contains("Only 2 good photos"))
        // Coverage can also be checked for any list (the results screen does this).
        assertNotNull(AlbumPlanner.coverage(a, emptyList()).single().warning)
    }

    // =====================================================================================
    // Story within each event
    // =====================================================================================

    @Test
    fun keyMomentsGetMorePicksAndEveryPartIsCovered() {
        var t = start
        fun moment(n: Int, gap: Long): List<PlanPhoto> = List(n) { photo(t = t + it * gap, faces = listOf(face())) }.also { t += 30 * minute }
        val arrival = moment(3, 20_000)
        val varmala = moment(40, 5_000)
        val m3 = moment(4, 20_000)
        val m4 = moment(3, 20_000)
        val close = moment(3, 20_000)
        val a = AlbumPlanner.analyze(arrival + varmala + m3 + m4 + close)
        assertEquals(5, a.moments.size)
        assertTrue(a.moments[1].key)
        assertFalse(a.moments[0].key)
        val s = AlbumPlanner.shortlist(a, 10)
        assertEquals(10, s.ids.size)
        for (m in listOf(arrival, varmala, m3, m4, close)) assertTrue(s.ids.any { id -> m.any { it.id == id } })
        assertTrue(s.ids.count { id -> varmala.any { it.id == id } } >= 3)
        assertTrue(s.reasons.values.flatten().any { it.startsWith("Key moment") })
        // Picks stay in time order.
        assertEquals(s.ids.sortedBy { a.byId.getValue(it).photo.takenAt }, s.ids)
    }

    @Test
    fun eventsShareTheAlbumByTheirGoodPhotos() {
        val haldi = List(20) { photo("Haldi", start + it * 10 * minute, listOf(face())) }
        val wedding = List(60) { photo("Wedding", start + 86_400_000L + it * 10 * minute, listOf(face())) }
        val a = AlbumPlanner.analyze(haldi + wedding)
        val s = AlbumPlanner.shortlist(a, 20)
        assertEquals(5, s.ids.count { a.byId.getValue(it).event == "Haldi" })
        assertEquals(15, s.ids.count { a.byId.getValue(it).event == "Wedding" })
        assertEquals(s.ids.sortedBy { a.byId.getValue(it).order }, s.ids)
    }

    // =====================================================================================
    // Variety and pacing
    // =====================================================================================

    @Test
    fun aboutTenPercentDetailShots() {
        val people = List(80) { photo("Mehendi", faces = listOf(face(300L + it))) }
        val details = List(20) { photo("Mehendi") }
        val a = AlbumPlanner.analyze(people + details)
        assertEquals(ShotType.DETAIL, a.byId.getValue(details[0].id).shot)
        val s = AlbumPlanner.shortlist(a, 20)
        val n = s.ids.count { a.byId.getValue(it).shot == ShotType.DETAIL }
        assertTrue("got $n details", n in 2..4)
    }

    @Test
    fun noRepeatedPoseBackToBack() {
        val h = 0L
        val first = photo(t = start, hash = h, faces = listOf(face(80L)))
        val samePose = photo(t = start + 10_000, hash = h xor 0x7FFFL, faces = listOf(face(80L))) // 15 bits apart: not a duplicate
        val different = photo(t = start + 20_000, faces = listOf(face(81L, expr = Expression.NATURAL)))
        val a = AlbumPlanner.analyze(listOf(first, samePose, different))
        assertTrue(a.byId.getValue(samePose.id).usable)
        val s = AlbumPlanner.shortlist(a, 2)
        assertTrue(different.id in s.ids)
        assertEquals(1, s.ids.count { it == first.id || it == samePose.id })
    }

    @Test
    fun portraitAndLandscapeAreBalanced() {
        val landscape = List(10) { photo(name = "A_%02d.jpg".format(it), faces = listOf(face())) }
        val portrait = List(4) { photo(name = "B_%02d.jpg".format(it), faces = listOf(face(sharp = 95.0)), portrait = true) }
        val a = AlbumPlanner.analyze(landscape + portrait)
        val s = AlbumPlanner.shortlist(a, 8)
        assertTrue(s.ids.any { a.byId.getValue(it).photo.portrait })
    }

    // =====================================================================================
    // Selection rules
    // =====================================================================================

    @Test
    fun removedPhotosAreNeverPickedAndReplaceRefills() {
        val photos = List(10) { photo(t = start + it * 10 * minute, faces = listOf(face(sharp = 90.0 + it))) }
        val a = AlbumPlanner.analyze(photos)
        val first = AlbumPlanner.shortlist(a, 5).ids
        val gone = first.first()
        val kept = (first - gone).toSet()
        val refilled = AlbumPlanner.shortlist(a, 5, removed = setOf(gone), keep = kept)
        assertEquals(5, refilled.ids.size)
        assertFalse(gone in refilled.ids)
        assertTrue(refilled.ids.containsAll(kept))
        assertFalse(gone in AlbumPlanner.shortlist(a, 10, removed = setOf(gone)).ids)
    }

    @Test
    fun notEnoughCleanPhotosFillsWithTheBestOfTheRest() {
        val good = photo(faces = listOf(face()))
        val blinking = photo(faces = listOf(face(eyes = EyeState.CLOSED)))
        val blinkingAndDark = photo(faces = listOf(face(eyes = EyeState.CLOSED)), brightness = 10.0)
        val broken = photo(failed = true)
        val a = AlbumPlanner.analyze(listOf(good, blinking, blinkingAndDark, broken))
        assertEquals(setOf(good.id, blinking.id), AlbumPlanner.shortlist(a, 2).ids.toSet())
        val all = AlbumPlanner.shortlist(a, 10)
        assertEquals(3, all.ids.size)
        assertFalse(broken.id in all.ids)
        assertTrue(all.reasons.getValue(blinking.id).first().startsWith("Added to fill"))
    }

    @Test
    fun everyPickSaysWhyItWasPicked() {
        val photos = List(20) { couplePhoto(t = start + it * 20 * minute) } + List(5) { photo(t = start + (500 + it * 20) * minute) }
        val a = AlbumPlanner.analyze(photos, couple)
        val s = AlbumPlanner.shortlist(a, 10)
        for (id in s.ids) assertTrue(s.reasons.getValue(id).isNotEmpty())
    }

    @Test
    fun allocateIsProportionalWithLargestRemainder() {
        assertEquals(mapOf("A" to 5, "B" to 3, "C" to 2), AlbumPlanner.allocate(linkedMapOf("A" to 50, "B" to 30, "C" to 20), 10))
        val q = AlbumPlanner.allocate(linkedMapOf("A" to 1000, "B" to 2, "C" to 1), 10)
        assertEquals(1, q["B"]); assertEquals(1, q["C"]); assertEquals(8, q["A"])
        assertEquals(mapOf("B" to 1, "C" to 1), AlbumPlanner.allocate(linkedMapOf("A" to 5, "B" to 50, "C" to 10), 2))
        assertEquals(mapOf("A" to 3), AlbumPlanner.allocate(linkedMapOf("A" to 3, "B" to 0), 10))
        assertEquals(0, AlbumPlanner.allocate(linkedMapOf("A" to 100, "B" to 1), 10, atLeastOne = false)["B"] ?: 0)
    }

    @Test
    fun emptyInputs() {
        val a = AlbumPlanner.analyze(emptyList())
        assertTrue(a.events.isEmpty())
        assertTrue(AlbumPlanner.shortlist(a, 10).ids.isEmpty())
        assertTrue(AlbumPlanner.shortlist(AlbumPlanner.analyze(listOf(photo())), 0).ids.isEmpty())
    }

    @Test
    fun bigWeddingIsQuickAndSane() {
        val rnd = java.util.Random(11)
        val events = listOf("01 Haldi", "02 Mehendi", "03 Sangeet", "04 Wedding", "05 Vidaai", "06 Reception")
        val photos = (0 until 10_000).map { i ->
            val e = i * events.size / 10_000
            val t = start + e * 86_400_000L + (i % 1700) * (5_000L + rnd.nextInt(60_000))
            val n = listOf(0, 1, 1, 2, 2, 3, 5, 8)[rnd.nextInt(8)]
            val faces = List(n) { k ->
                val person = when (rnd.nextInt(10)) { 0, 1, 2 -> BRIDE; 3, 4 -> GROOM; else -> 10L + rnd.nextInt(120) }
                face(person, x = 0.02f + k * 0.12f, w = 0.04f + rnd.nextFloat() * 0.08f,
                    eyes = EyeState.values()[rnd.nextInt(4)], expr = Expression.values()[rnd.nextInt(5)],
                    looking = rnd.nextBoolean(), dir = rnd.nextFloat() * 2 - 1, sharp = 20.0 + rnd.nextInt(150))
            }
            photo(events[e], t, faces, peak = 20.0 + rnd.nextInt(150), brightness = 30.0 + rnd.nextInt(200), portrait = rnd.nextInt(3) == 0)
        }
        val tags = PeopleTags(BRIDE, GROOM, (10L..40L).associateWith {
            PersonTag(it, "P$it", Priority.values()[rnd.nextInt(4)], Side.values()[rnd.nextInt(3)], elder = it % 7 == 0L)
        })
        val began = System.currentTimeMillis()
        val a = AlbumPlanner.analyze(photos, tags)
        val s = AlbumPlanner.shortlist(a, 400)
        val took = System.currentTimeMillis() - began
        assertTrue("took $took ms", took < 20_000)
        assertEquals(400, s.ids.size)
        assertEquals(400, s.ids.toSet().size)
        assertEquals(events, a.events)
        assertEquals(s.ids.sortedBy { a.byId.getValue(it).order }, s.ids)
        for (e in events) assertTrue(s.ids.any { a.byId.getValue(it).event == e })
        assertTrue(s.ids.all { s.reasons.getValue(it).isNotEmpty() })
    }
}
