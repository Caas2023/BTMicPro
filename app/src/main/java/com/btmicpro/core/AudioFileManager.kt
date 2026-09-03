package com.btmicpro.core

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Gerenciador de armazenamento e compartilhamento de arquivos de áudio gravados pelo aplicativo.
 *
 * V2: Corrige duração para sampleRate variável (16k vs 48k) e lê header real do WAV.
 */
class AudioFileManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null

    /**
     * Retorna a pasta de destino onde as gravações de áudio são salvas.
     */
    private fun getRecordingsDirectory(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Cria um arquivo temporário em disco para streaming contínuo de gravação.
     */
    fun createTempPcmFile(): File {
        val tempDir = File(context.cacheDir, "temp_audio")
        if (!tempDir.exists()) tempDir.mkdirs()
        return File.createTempFile("rec_stream_", ".pcm", tempDir)
    }

    /**
     * Salva o arquivo PCM temporário em um arquivo de áudio WAV final via streaming direto de disco,
     * garantindo consumo mínimo e constante de memória RAM.
     */
    suspend fun savePcmFileAsWav(
        tempPcmFile: File,
        sampleRate: Int,
        channels: Int,
        durationMs: Long
    ): File = withContext(Dispatchers.IO) {
        val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "BTMic_${timestampFormat.format(Date())}_${sampleRate}Hz.wav"
        val outputFile = File(getRecordingsDirectory(), fileName)

        val totalAudioLen = tempPcmFile.length()
        FileOutputStream(outputFile).use { fos ->
            writeWavHeader(fos, totalAudioLen, sampleRate, channels)
            tempPcmFile.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
            }
        }
        try { tempPcmFile.delete() } catch (ignored: Exception) {}

        outputFile
    }

    /**
     * Salva dados PCM em um arquivo de áudio WAV válido com cabeçalho RIFF padrão.
     *
     * @param pcmData Bytes PCM brutos capturados.
     * @param sampleRate Taxa de amostragem (ex: 48000 Hz ou 16000 Hz em SCO).
     * @param channels Número de canais (1 = mono).
     * @param durationMs Duração calculada do áudio.
     * @return O objeto File do áudio gravado.
     */
    suspend fun savePcmAsWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        durationMs: Long
    ): File = withContext(Dispatchers.IO) {
        val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "BTMic_${timestampFormat.format(Date())}_${sampleRate}Hz.wav"
        val outputFile = File(getRecordingsDirectory(), fileName)

        FileOutputStream(outputFile).use { fos ->
            // Escreve cabeçalho WAV preliminar de 44 bytes
            writeWavHeader(fos, pcmData.size.toLong(), sampleRate, channels)
            // Escreve os dados PCM processados
            fos.write(pcmData)
        }

        outputFile
    }

    /**
     * Escreve o cabeçalho canônico RIFF / WAVE no fluxo de saída.
     */
    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 16 / 8).toLong()

        val header = ByteArray(44)
        // RIFF chunk
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE chunk
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // fmt sub-chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Tamanho do sub-chunk fmt (16 para PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Formato de áudio (1 = PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // Block align
        header[33] = 0
        header[34] = 16 // Bits por amostra
        header[35] = 0
        // data sub-chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    /**
     * Lista todos os arquivos de gravação salvos no armazenamento local.
     */
    suspend fun getSavedRecordings(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) }
            ?: return@withContext emptyList()

        files.sortedByDescending { it.lastModified() }.map { file ->
            val durationMs = calculateWavDurationMs(file)
            RecordingItem(
                id = file.name,
                fileName = file.name,
                filePath = file.absolutePath,
                timestamp = file.lastModified(),
                durationMs = durationMs,
                isProcessedWithAi = true
            )
        }
    }

    /**
     * Calcula a duração lendo o header WAV real (sampleRate variável 16k/48k).
     */
    private fun calculateWavDurationMs(file: File): Long {
        return try {
            // Tenta ler sampleRate do header WAV (bytes 24-27)
            val sampleRate = readWavSampleRate(file) ?: 16000 // fallback
            val byteRate = sampleRate * 1 * 16 / 8 // mono 16-bit
            val audioLength = file.length() - 44
            if (audioLength > 0 && byteRate > 0) {
                (audioLength * 1000L) / byteRate
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun readWavSampleRate(file: File): Int? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 44) return null
                raf.seek(24)
                val b0 = raf.read() and 0xFF
                val b1 = raf.read() and 0xFF
                val b2 = raf.read() and 0xFF
                val b3 = raf.read() and 0xFF
                b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler sampleRate do WAV", e)
            null
        }
    }

    /**
     * Exclui um arquivo de gravação.
     */
    suspend fun deleteRecording(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir arquivo de áudio", e)
            false
        }
    }

    /**
     * Cria e inicia a Intent de compartilhamento do áudio diretamente com o WhatsApp ou outros mensageiros.
     *
     * @param filePath Caminho absoluto do arquivo a ser compartilhado.
     */
    fun shareAudioToWhatsApp(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Arquivo não encontrado para compartilhamento: $filePath")
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Enviar áudio tratado via:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            Log.d(TAG, "Intent de compartilhamento aberta para: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir compartilhamento de áudio", e)
        }
    }

    /**
     * Reproduz ou pausa um arquivo de áudio no player embutido.
     *
     * @param filePath Caminho do áudio.
     * @param onComplete Callback executado ao término da reprodução.
     * @return True se iniciou a reprodução, False se pausou.
     */
    fun togglePlayback(filePath: String, onComplete: () -> Unit): Boolean {
        if (mediaPlayer != null && currentlyPlayingPath == filePath) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                return false
            } else {
                mediaPlayer?.start()
                return true
            }
        }

        // Para qualquer áudio em reprodução
        stopPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                start()
            }
            currentlyPlayingPath = filePath
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir áudio", e)
            stopPlayback()
            return false
        }
    }

    /**
     * Interrompe a reprodução de áudio.
     */
    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingPath = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao interromper reprodução", e)
        }
    }

    companion object {
        private const val TAG = "AudioFileManager"
    }
}
