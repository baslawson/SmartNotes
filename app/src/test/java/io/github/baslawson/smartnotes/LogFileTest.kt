package io.github.baslawson.smartnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun appendsAndReadsInOrder() {
        val log = LogFile(File(tmp.root, "logs"))
        log.append("one"); log.append("two")
        assertEquals("one\ntwo\n", log.read())
    }

    @Test fun rotatesAndKeepsAtMostTwoFiles() {
        val dir = File(tmp.root, "logs")
        val log = LogFile(dir, maxBytes = 100)
        repeat(50) { log.append("line %03d xxxxxxxxxxxxxxx".format(it)) }
        val text = log.read()
        assertTrue(text.endsWith("line 049 xxxxxxxxxxxxxxx\n"))
        assertTrue(text.length < 300)
        // Oldest first, without gaps inside what is kept.
        val numbers = text.lines().filter { it.isNotEmpty() }.map { it.substring(5, 8).toInt() }
        assertEquals((numbers.first()..49).toList(), numbers)
        assertEquals(setOf("app.log", "app.log.1"), dir.list()!!.toSet())
    }

    @Test fun clear() {
        val log = LogFile(File(tmp.root, "logs"))
        log.append("x")
        log.clear()
        assertEquals("", log.read())
    }
}
