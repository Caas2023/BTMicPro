package com.btmicpro.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class FlightLogStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun restartAndExportPreserveAllDaysBeyondTheMemoryTail() {
        val directory = temporary.newFolder("cache")
        var now = 1_790_208_000_000L
        val writer = FlightLogStore(directory, clock = { now })
        repeat(1600) { writer.append("day1-event-$it") }
        now += 24 * 60 * 60 * 1000L
        val restarted = FlightLogStore(directory, clock = { now })
        restarted.append("day2-profile-mode6")
        assertEquals("day2-profile-mode6", restarted.readRecent().last())
        assertEquals(400, restarted.readRecent().size)
        val zip = File(temporary.root, "export.zip")
        restarted.exportZip(zip, "diagnostico atual")
        ZipFile(zip).use { archive ->
            val text = archive.entries().asSequence().filter { it.name.startsWith("logs/") }
                .joinToString("") { archive.getInputStream(it).bufferedReader().readText() }
            assertTrue(text.contains("day1-event-0\n"))
            assertTrue(text.contains("day1-event-1599\n"))
            assertTrue(text.contains("day2-profile-mode6\n"))
            assertEquals(1601, text.lineSequence().count { it.isNotBlank() })
            assertEquals("diagnostico atual", archive.getInputStream(archive.getEntry("diagnostico.txt")).bufferedReader().readText())
        }
    }

    @Test fun sizeRotationDropsOldestSegmentsAndNeverTouchesUnrelatedFiles() {
        val directory = temporary.newFolder("cache")
        val unrelated = File(directory, "user.txt").apply { writeText("keep") }
        val store = FlightLogStore(directory, maxBytes = 24, segmentBytes = 12)
        repeat(6) { store.append("event-$it") }
        assertTrue(directory.listFiles()!!.filter { it.extension == "log" }.sumOf { it.length() } <= 24)
        val tail = store.readRecent()
        assertFalse(tail.contains("event-0"))
        assertEquals("event-5", tail.last())
        store.clear()
        assertTrue(store.readRecent().isEmpty())
        assertEquals("keep", unrelated.readText())
    }

    @Test fun ageRetentionSurvivesCacheRecreationAndKeepsRecentEvents() {
        val directory = temporary.newFolder("cache")
        var now = 1_790_208_000_000L
        val store = FlightLogStore(directory, retentionMs = 1000, clock = { now })
        store.append("expired")
        now += 2000
        store.prune()
        assertTrue(store.readRecent().isEmpty())
        directory.delete()
        store.append("cache-recreated")
        assertEquals(listOf("cache-recreated"), store.readRecent())
    }

    @Test fun concurrentCrashAndWorkerWritesRemainWhole() {
        val store = FlightLogStore(temporary.newFolder("cache"))
        val threads = (1..4).map { worker -> Thread { repeat(100) { store.append("worker-$worker-event-$it") } }.apply { start() } }
        threads.forEach { it.join() }
        val lines = store.readRecent(500)
        assertEquals(400, lines.size)
        assertEquals(400, lines.toSet().size)
        assertTrue(lines.all { it.matches(Regex("worker-[1-4]-event-\\d+")) })
    }
}
