package com.btmicpro.core

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.btmicpro.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Logs em cache por até 7 dias/32 MiB; exportação inclui todas as sessões retidas. */
object AppLogger {
    private const val MAX_MEMORY_LOGS = 1500
    private val sessionId = UUID.randomUUID().toString().take(8)
    private val sequence = AtomicLong()
    private val dropped = AtomicInteger()
    private val memoryLogs = ArrayDeque<String>()
    private val logLock = Any()
    private val _logsState = MutableStateFlow<List<String>>(emptyList())
    val logsState: StateFlow<List<String>> = _logsState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private sealed interface Command {
        data class Line(val value: String, val time: Long) : Command
        data class Export(val diagnostics: String, val result: CompletableDeferred<File>) : Command
        data object Clear : Command
    }
    private val channel = Channel<Command>(2048)
    @Volatile private var store: FlightLogStore? = null
    @Volatile private var profile = "unknown"
    @Volatile private var storageError: String? = null
    private var initialized = false
    private lateinit var exportDirectory: File

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        profile = app.getSharedPreferences(com.btmicpro.receiver.BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .getString("audio_mode_profile", "standard") ?: "standard"
        val disk = FlightLogStore(File(app.cacheDir, "flight_recorder"))
        store = disk
        exportDirectory = File(app.cacheDir, "log_exports")
        scope.launch {
            try {
                synchronized(logLock) {
                    val pending = memoryLogs.toList()
                    memoryLogs.clear()
                    (disk.readRecent() + pending).takeLast(MAX_MEMORY_LOGS).forEach(memoryLogs::addLast)
                    _logsState.value = memoryLogs.toList()
                }
            } catch (e: Exception) { reportStorageError(e) }
            for (command in channel) {
                try {
                    val lost = dropped.getAndSet(0)
                    if (lost > 0) disk.append(format("WARN ", "LOGGER_OVERFLOW", "$lost eventos descartados por fila cheia"))
                    when (command) {
                        is Command.Line -> disk.append(command.value, command.time)
                        is Command.Export -> {
                            exportDirectory.mkdirs()
                            // Mantém dois pacotes anteriores; nunca inclui o ZIP dentro de si mesmo.
                            exportDirectory.listFiles()?.filter { it.extension == "zip" }
                                ?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.delete() }
                            val file = File(exportDirectory, "BTMicPro_logs_${System.currentTimeMillis()}.zip")
                            disk.exportZip(file, command.diagnostics + "\n\n" + storageStatus())
                            command.result.complete(file)
                        }
                        Command.Clear -> disk.clear()
                    }
                    storageError = null
                } catch (e: Exception) {
                    reportStorageError(e)
                    if (command is Command.Export) command.result.completeExceptionally(e)
                }
            }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val fatal = format("ERROR", "CRASH", "thread=${thread.name}\n${Log.getStackTraceString(error)}")
            // A fila pode não sobreviver ao processo; o crash é escrito sincronamente.
            try { disk.append(fatal) } catch (e: Exception) { reportStorageError(e) }
            previous?.uncaughtException(thread, error)
        }
        i("SESSION_START", "version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}); " +
            "device=${Build.MANUFACTURER}/${Build.MODEL}; sdk=${Build.VERSION.SDK_INT}; build=${Build.DISPLAY}; retention=7d/32MiB")
    }

    fun setProfile(code: String) { profile = code }
    fun storageStatus(): String = storageError?.let { "Falha no cache: $it" }
        ?: "Cache: até 7 dias / 32 MiB. Exportar ZIP inclui sessões anteriores. O Android pode limpar o cache."

    private fun reportStorageError(error: Exception) {
        val message = error.javaClass.simpleName + ": " + error.message
        if (storageError != message) Log.e("AppLogger", "Falha ao persistir logs", error)
        storageError = message
    }

    fun d(tag: String, message: String) { Log.d(tag, message); record("DEBUG", tag, message) }
    fun i(tag: String, message: String) { Log.i(tag, message); record("INFO ", tag, message) }
    fun w(tag: String, message: String) { Log.w(tag, message); record("WARN ", tag, message) }
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        record("ERROR", tag, message + (throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
    }
    fun audio(tag: String, message: String) { Log.d(tag, message); record("AUDIO", tag, message) }

    private fun format(level: String, tag: String, message: String): String =
        "[${ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}] [$level] [$tag] " +
            "session=$sessionId seq=${sequence.incrementAndGet()} pid=${Process.myPid()} " +
            "elapsedMs=${SystemClock.elapsedRealtime()} profile=$profile $message"

    private fun record(level: String, tag: String, message: String) {
        val line = format(level, tag, message.take(60 * 1024))
        synchronized(logLock) {
            if (memoryLogs.size >= MAX_MEMORY_LOGS) memoryLogs.removeFirst()
            memoryLogs.addLast(line)
            _logsState.value = memoryLogs.toList()
        }
        if (!channel.trySend(Command.Line(line, System.currentTimeMillis())).isSuccess) dropped.incrementAndGet()
    }

    fun getAllLogsText(): String = synchronized(logLock) { memoryLogs.joinToString("\n") }

    suspend fun exportLogs(diagnostics: String): File {
        check(initialized) { "Logger não inicializado" }
        val result = CompletableDeferred<File>()
        channel.send(Command.Export(diagnostics, result)) // Barreira: todas as linhas anteriores foram escritas.
        return result.await()
    }

    fun clearLogs() {
        scope.launch {
            channel.send(Command.Clear)
            synchronized(logLock) { memoryLogs.clear(); _logsState.value = emptyList() }
            i("AppLogger", "Logs limpos pelo usuário")
        }
    }
}
