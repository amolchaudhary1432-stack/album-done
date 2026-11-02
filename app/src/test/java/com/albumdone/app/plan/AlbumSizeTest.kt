package com.albumdone.app.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSizeTest {

    @Test
    fun aThousandPhotosSuggestsAHundredAndFifty() {
        assertEquals(150, AlbumSize.suggested(1000))
        assertEquals(listOf(100, 120, 150, 200), AlbumSize.choices(1000))
    }

    @Test
    fun aWholeWeddingDayOfTwoAndAHalfThousand() {
        assertEquals(2500, AlbumSize.MAX_PHOTOS)
        assertEquals(380, AlbumSize.suggested(2500))
        assertEquals(listOf(200, 250, 380, 500), AlbumSize.choices(2500))
    }

    @Test
    fun choicesAlwaysIncludeTheSuggestionAndFitTheFolder() {
        for (n in listOf(5, 12, 40, 206, 333, 600, 999, 1000, 1700, 2499, 2500)) {
            val s = AlbumSize.suggested(n)
            val c = AlbumSize.choices(n)
            assertTrue("$n: $c has $s", s in c)
            assertTrue("$n: $c", c.all { it in minOf(10, n)..n } && c == c.sorted() && c.size in 1..4)
        }
    }

    @Test
    fun tinyFoldersStillGetAnAlbum() {
        assertEquals(10, AlbumSize.suggested(40))
        assertEquals(listOf(10), AlbumSize.choices(40))
        assertEquals(5, AlbumSize.suggested(5))
        assertEquals(0, AlbumSize.suggested(0))
    }
}
