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
     * Salva dados PCM em um arquivo de áudio WAV válido com cabeçalho RIFF padrão.
     *
     * @param pcmData Bytes PCM brutos capturados.
     * @param sampleRate Taxa de amostragem (ex: 48000 Hz).
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
        val fileName = "BTMic_${timestampFormat.format(Date())}.wav"
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
     * Calcula a duração aproximada de um arquivo WAV em milissegundos.
     */
    private fun calculateWavDurationMs(file: File): Long {
        return try {
            val audioLength = file.length() - 44
            if (audioLength > 0) {
                // 48000 Hz, 16-bit mono = 96.000 bytes por segundo = 96 bytes por milissegundo
                audioLength / 96
            } else 0L
        } catch (e: Exception) {
            0L
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
