package com.btmicpro.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log

/**
 * CommunicationDeviceManager — Especialista na gestão do Dispositivo de Comunicação do Android.
 * Centraliza o uso das APIs modernas do Android 12+ (API 31+) e rotinas de fallback legadas.
 */
class CommunicationDeviceManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Retorna a lista de dispositivos de comunicação disponíveis no sistema operacional.
     */
    fun getAvailableCommunicationDevices(): List<AudioDeviceInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.availableCommunicationDevices
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao obter availableCommunicationDevices", e)
                emptyList()
            }
        } else {
            // Em versões anteriores ao Android 12, expõe dispositivos de áudio gerais
            try {
                audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Retorna o dispositivo de comunicação atualmente selecionado pelo sistema operacional.
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
     * Localiza o melhor dispositivo Bluetooth para comunicação de voz (Intercomunicador / Headset).
     *
     * Critérios de seleção (Item 15 do Prompt Master):
     * 1. Presença na lista de dispositivos de comunicação disponíveis.
     * 2. Tipo compatível com canal de voz bidirecional (TYPE_BLUETOOTH_SCO ou TYPE_BLE_HEADSET).
     * 3. Capacidade de gravação de microfone (isSource).
     */
    fun findBestBluetoothCommunicationDevice(): AudioDeviceInfo? {
        val available = getAvailableCommunicationDevices()

        // 1ª Prioridade: Dispositivo explicitamente marcado como TYPE_BLUETOOTH_SCO (Intercomunicadores HFP/SCO)
        val scoDevice = available.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (scoDevice != null) {
            Log.d(TAG, "Melhor dispositivo encontrado (SCO): ${scoDevice.productName}")
            return scoDevice
        }

        // 2ª Prioridade: Headset BLE Audio (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bleDevice = available.find { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
            if (bleDevice != null) {
                Log.d(TAG, "Melhor dispositivo encontrado (BLE Headset): ${bleDevice.productName}")
                return bleDevice
            }
        }

        // 3ª Prioridade: Fallback para dispositivos conectados em AudioManager.getDevices
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val fallbackBt = inputDevices.find { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }

        if (fallbackBt != null) {
            Log.d(TAG, "Dispositivo Bluetooth detectado nos inputs gerais: ${fallbackBt.productName}")
            return fallbackBt
        }

        Log.w(TAG, "Nenhum dispositivo Bluetooth de comunicação elegível encontrado.")
        return null
    }

    /**
     * Seleciona o dispositivo de comunicação fornecido e valida se a seleção foi aceita pelo sistema.
     * Nunca assume que o retorno 'true' é garantia: consulta o hardware novamente (Item 13).
     */
    fun selectCommunicationDevice(device: AudioDeviceInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val callResult = audioManager.setCommunicationDevice(device)
                if (!callResult) {
                    Log.w(TAG, "setCommunicationDevice retornou falso para ${device.productName}")
                    return false
                }

                // Validação estrita: consulta o dispositivo atual no sistema
                val confirmedDevice = getCurrentCommunicationDevice()
                val isConfirmed = (confirmedDevice?.id == device.id) || (confirmedDevice?.type == device.type)
                Log.i(TAG, "setCommunicationDevice: callResult=$callResult, confirmadoNoSistema=$isConfirmed (${confirmedDevice?.productName})")

                // Aplica captura preferencial para evitar que WhatsApp use o microfone embutido
                applyPreferredCapturePreset(device)
                return isConfirmed
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao selecionar communication device", e)
                return false
            }
        } else {
            // Android 11 ou inferior (API Legada - Item 14)
            return try {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "API Legada: startBluetoothSco disparado")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao disparar startBluetoothSco legado", e)
                false
            }
        }
    }

    /**
     * Limpa a seleção do dispositivo de comunicação, retornando o sistema ao estado padrão.
     */
    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
                clearPreferredCapturePresets()
                Log.i(TAG, "Dispositivo de comunicação limpo com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao limpar communication device", e)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
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
