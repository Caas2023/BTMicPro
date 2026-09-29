package com.btmicpro.core

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Armazenamento rotativo compartilhado pelo escritor assíncrono, exportação e crash handler. */
internal class FlightLogStore(
    private val directory: File,
    private val retentionMs: Long = 7L * 24 * 60 * 60 * 1000,
    private val maxBytes: Long = 32L * 1024 * 1024,
    private val segmentBytes: Long = 2L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private fun files(): List<File> = directory.listFiles()?.filter {
        it.isFile && it.name.matches(Regex("flight-\\d{4}-\\d{2}-\\d{2}-\\d{5}\\.log"))
    }?.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name }) ?: emptyList()

    @Synchronized
    fun append(line: String, timestamp: Long = clock()) {
        check(directory.isDirectory || directory.mkdirs()) { "Cache de logs indisponível" }
        val day = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString()
        val prefix = "flight-$day-"
        val bytes = (line.take(64 * 1024) + "\n").toByteArray(Charsets.UTF_8)
        val last = files().filter { it.name.startsWith(prefix) }.maxByOrNull { it.name }
        val file = if (last != null && last.length() + bytes.size <= segmentBytes) last else {
            val index = (last?.name?.removePrefix(prefix)?.removeSuffix(".log")?.toIntOrNull() ?: -1) + 1
            File(directory, "$prefix${index.toString().padStart(5, '0')}.log")
        }
        file.appendBytes(bytes)
        file.setLastModified(timestamp)
        prune()
    }

    @Synchronized
    fun prune() {
        val now = clock()
        files().filter { now - it.lastModified() > retentionMs }.forEach { it.delete() }
        val remaining = files()
        var size = remaining.sumOf { it.length() }
        for (file in remaining) {
            if (size <= maxBytes) break
            val bytes = file.length()
            if (file.delete()) size -= bytes
        }
    }

    @Synchronized
    fun readRecent(maxLines: Int = 400): List<String> {
        prune()
        val result = ArrayDeque<String>()
        for (file in files().asReversed()) {
            val text = RandomAccessFile(file, "r").use { input ->
                val offset = (input.length() - 256 * 1024).coerceAtLeast(0)
                input.seek(offset)
                val bytes = ByteArray((input.length() - offset).toInt())
                input.readFully(bytes)
                bytes.toString(Charsets.UTF_8).let { if (offset > 0) it.substringAfter('\n', "") else it }
            }
            val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
            lines.takeLast(maxLines - result.size).asReversed().forEach { result.addFirst(it) }
            if (result.size >= maxLines) break
        }
        return result.toList()
    }

    @Synchronized
    fun exportZip(destination: File, diagnostics: String) {
        prune()
        destination.parentFile?.mkdirs()
        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("diagnostico.txt"))
            zip.write(diagnostics.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (file in files()) {
                zip.putNextEntry(ZipEntry("logs/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    @Synchronized
    fun clear() { files().forEach { it.delete() } }
}
