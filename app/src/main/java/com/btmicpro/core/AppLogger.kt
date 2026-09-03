package com.btmicpro.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * AppLogger — Sistema de Registro Contínuo e Flight Recorder de Áudio/Sistema.
 *
 * Funcionalidades:
 * - Buffer circular thread-safe em memória (últimos 1500 registros formatados com data/hora e milissegundos).
 * - Escrita assíncrona desacoplada em arquivo de log (btmic_flight_recorder.log) sem impacto na latência do áudio.
 * - StateFlow reativo para exibição em tempo real na interface Jetpack Compose.
 * - Captura global de exceções não tratadas (UncaughtExceptionHandler).
 * - Telemetria especializada de áudio: RMS, picos em dBFS, clipping e cortes de sinal.
 */
object AppLogger {

    private const val MAX_MEMORY_LOGS = 1500
    private const val LOG_FILE_NAME = "btmic_flight_recorder.log"

    private val memoryLogs = ArrayDeque<String>(MAX_MEMORY_LOGS)
    private val logLock = Any()

    private val _logsState = MutableStateFlow<List<String>>(emptyList())
    val logsState: StateFlow<List<String>> = _logsState.asStateFlow()

    private val logChannel = Channel<String>(capacity = 500)
    private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var logFile: File? = null
    private var isInitialized = false

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, LOG_FILE_NAME)

            // Inicia o consumidor assíncrono de escrita em disco
            loggerScope.launch {
                for (line in logChannel) {
                    try {
                        logFile?.let { file ->
                            FileWriter(file, true).use { writer ->
                                writer.appendLine(line)
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }

            // Capturador global de crash
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                e("CRASH_HANDLER", "CRASH FATAL NÃO TRATADO na Thread [${thread.name}]: ${throwable.message}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }

            i("AppLogger", "=== FLIGHT RECORDER INICIALIZADO COM SUCESSO ===")
        } catch (e: Exception) {
            Log.e("AppLogger", "Falha ao inicializar arquivo de log", e)
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        record("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        record("INFO ", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        record("WARN ", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            record("ERROR", tag, "$message\n$sw")
        } else {
            Log.e(tag, message)
            record("ERROR", tag, message)
        }
    }

    /**
     * Registro de telemetria acústica especializada.
     */
    fun audio(tag: String, message: String) {
        Log.d(tag, "[AUDIO] $message")
        record("AUDIO", tag, message)
    }

    private fun record(level: String, tag: String, message: String) {
        val timestamp = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        val formattedLine = "[$timestamp] [$level] [$tag] $message"

        synchronized(logLock) {
            if (memoryLogs.size >= MAX_MEMORY_LOGS) {
                memoryLogs.removeFirst()
            }
            memoryLogs.addLast(formattedLine)
            _logsState.value = memoryLogs.toList()
        }

        logChannel.trySend(formattedLine)
    }

    /**
     * Retorna todos os logs em formato de texto concatenado.
     */
    fun getAllLogsText(): String {
        synchronized(logLock) {
            return if (memoryLogs.isEmpty()) {
                "Nenhum log registrado até o momento."
            } else {
                memoryLogs.joinToString(separator = "\n")
            }
        }
    }

    /**
     * Limpa o buffer de logs em memória e trunca o arquivo físico.
     */
    fun clearLogs() {
        synchronized(logLock) {
            memoryLogs.clear()
            _logsState.value = emptyList()
        }
        loggerScope.launch {
            try {
                logFile?.writeText("")
            } catch (ignored: Exception) {}
        }
        i("AppLogger", "Logs limpos pelo usuário.")
    }

    /**
     * Retorna o arquivo de log para compartilhamento.
     */
    fun getLogFile(): File? = logFile
}
