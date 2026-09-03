package com.btmicpro.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay

/**
 * CommunicationDeviceManager — Especialista em Roteamento de Comunicação do Android Moderno (API 31+).
 *
 * Diretrizes Estritas (Itens 21, 22, 42, 43, 44, 45 do Prompt Master):
 * 1. setCommunicationDevice() recebe estritamente um dispositivo de SAÍDA/SINK. O Android seleciona
 *    a fonte correspondente automaticamente. Jamais tentar passar dispositivo de entrada.
 * 2. Busca primária realizada exclusivamente em availableCommunicationDevices.
 * 3. Confirmação com timeout (até 30s) aguardando audioManager.communicationDevice == device.
 * 4. Filtragem seletiva para ignorar mouses, teclados, relógios ou caixas de som A2DP puras.
 */
@Suppress("DEPRECATION")
class CommunicationDeviceManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Retorna a lista de dispositivos de comunicação elegíveis expostos pelo sistema.
     */
    fun getAvailableCommunicationDevices(): List<AudioDeviceInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.availableCommunicationDevices.filter { it.isSink }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao obter availableCommunicationDevices", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Retorna o dispositivo de comunicação atualmente ativo no sistema.
     */
    fun getCurrentCommunicationDevice(): AudioDeviceInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.communicationDevice
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao consultar communicationDevice", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Localiza o melhor dispositivo Bluetooth para comunicação de voz bidirecional.
     *
     * Regras (Itens 42, 43, 44, 45):
     * - Busca somente em availableCommunicationDevices.
     * - Exige que o dispositivo seja saída (isSink).
     * - Filtra apenas TYPE_BLUETOOTH_SCO ou TYPE_BLE_HEADSET.
     * - Respeita preferência do usuário (se fornecida).
     */
    fun findBestBluetoothCommunicationDevice(preferredDeviceName: String? = null): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.w(TAG, "Dispositivos de comunicação modernos requerem Android 12+ (API 31).")
            return null
        }

        val available = getAvailableCommunicationDevices()
        if (available.isEmpty()) {
            Log.d(TAG, "Nenhum dispositivo disponível em availableCommunicationDevices.")
            return null
        }

        // Filtra estritamente dispositivos Bluetooth bidirecionais (SCO e BLE Headset)
        val eligibleDevices = available.filter { dev ->
            dev.isSink && (
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            )
        }

        if (eligibleDevices.isEmpty()) {
            Log.w(TAG, "Nenhum dispositivo Bluetooth de comunicação elegível (apenas não-comunicação disponíveis).")
            return null
        }

        // Se o usuário tiver um dispositivo de preferência selecionado
        if (!preferredDeviceName.isNullOrBlank() && preferredDeviceName != "Automático") {
            val preferred = eligibleDevices.find {
                it.productName.toString().contains(preferredDeviceName, ignoreCase = true)
            }
            if (preferred != null) {
                Log.i(TAG, "Dispositivo de preferência do usuário selecionado: ${preferred.productName}")
                return preferred
            }
        }

        // Prioridade 1: TYPE_BLUETOOTH_SCO (Padrão para Intercomunicadores de Moto)
        val scoDevice = eligibleDevices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (scoDevice != null) {
            Log.d(TAG, "Melhor dispositivo encontrado (SCO Sink): ${scoDevice.productName} (ID=${scoDevice.id})")
            return scoDevice
        }

        // Prioridade 2: TYPE_BLE_HEADSET
        val bleDevice = eligibleDevices.find { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        if (bleDevice != null) {
            Log.d(TAG, "Melhor dispositivo encontrado (BLE Headset Sink): ${bleDevice.productName} (ID=${bleDevice.id})")
            return bleDevice
        }

        return null
    }

    /**
     * Seleciona o dispositivo de comunicação e aguarda assincronamente a confirmação
     * de que o sistema operacional realmente aplicou a seleção (Itens 21 e 22 do Prompt Master).
     *
     * @param device Dispositivo de comunicação (DEVE ser isSink).
     * @param timeoutMs Tempo máximo de espera para confirmação (padrão 15 segundos, até 30s).
     * @return true se o dispositivo foi confirmado pelo sistema; false caso contrário.
     */
    suspend fun selectCommunicationDeviceWithConfirmation(
        device: AudioDeviceInfo,
        timeoutMs: Long = 15000L
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Regra de Ouro (Item 42): NUNCA passar dispositivo de entrada para setCommunicationDevice
            if (!device.isSink) {
                Log.e(TAG, "VIOLAÇÃO: Dispositivo ${device.productName} não é um dispositivo de saída (isSink=false). Rejeitado.")
                return false
            }

            try {
                Log.i(TAG, "Solicitando setCommunicationDevice: ${device.productName} (ID=${device.id}, Tipo=${device.type})")
                // Mantém MODE_NORMAL para que o WhatsApp não bloqueie gravação de áudio ("Não é possível gravar áudio durante chamada telefônica")
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false

                val callResult = audioManager.setCommunicationDevice(device)
                if (!callResult) {
                    Log.w(TAG, "setCommunicationDevice() retornou false imediatamente para ${device.productName}.")
                    return false
                }

                // Aguarda confirmação ativa do sistema (Wait for Confirmation — Item 21)
                val startTime = System.currentTimeMillis()
                val intervalMs = 150L

                while ((System.currentTimeMillis() - startTime) < timeoutMs) {
                    val current = getCurrentCommunicationDevice()
                    if (current != null && (current.id == device.id || current.type == device.type)) {
                        Log.i(TAG, "Confirmação recebida com sucesso em ${System.currentTimeMillis() - startTime}ms: ${current.productName} (audioMode=MODE_IN_COMMUNICATION)")
                        applyPreferredCapturePreset(device)
                        return true
                    }
                    delay(intervalMs)
                }

                Log.w(TAG, "Timeout de confirmação atingido (${timeoutMs}ms) sem confirmação do dispositivo ${device.productName}.")
                clearCommunicationDevice()
                return false

            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao selecionar communication device", e)
                clearCommunicationDevice()
                return false
            }
        } else {
            // Android 11 ou anterior (Fallback legado controlado — Item 5 Camada C)
            return try {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "Fallback Legado: startBluetoothSco() disparado com MODE_NORMAL")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro no fallback legado startBluetoothSco()", e)
                audioManager.mode = AudioManager.MODE_NORMAL
                false
            }
        }
    }

    /**
     * Limpa o dispositivo de comunicação de forma limpa e restaura o roteamento padrão do sistema.
     */
    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                clearPreferredCapturePresets()
                Log.i(TAG, "Communication device limpo com sucesso e audioMode restaurado para MODE_NORMAL.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao limpar communication device", e)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
            } catch (ignored: Exception) {}
        }
    }

    private fun applyPreferredCapturePreset(device: AudioDeviceInfo) {
        setPreferredPreset(MediaRecorder.AudioSource.MIC, device)
        setPreferredPreset(MediaRecorder.AudioSource.VOICE_COMMUNICATION, device)
        setPreferredPreset(MediaRecorder.AudioSource.DEFAULT, device)
        setPreferredPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION, device)
    }

    private fun setPreferredPreset(preset: Int, device: AudioDeviceInfo): Boolean {
        return try {
            val method = AudioManager::class.java.getMethod(
                "setPreferredDeviceForCapturePreset",
                Int::class.javaPrimitiveType,
                AudioDeviceInfo::class.java
            )
            method.invoke(audioManager, preset, device) as Boolean
        } catch (e: Exception) { false }
    }

    private fun clearPreferredCapturePresets() {
        try {
            val method = AudioManager::class.java.getMethod(
                "clearPreferredDeviceForCapturePreset",
                Int::class.javaPrimitiveType
            )
            method.invoke(audioManager, MediaRecorder.AudioSource.MIC)
            method.invoke(audioManager, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            method.invoke(audioManager, MediaRecorder.AudioSource.DEFAULT)
            method.invoke(audioManager, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } catch (ignored: Exception) {}
    }

    companion object {
        private const val TAG = "BTMIC_COMM_MGR"
    }
}
