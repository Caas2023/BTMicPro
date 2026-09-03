package com.btmicpro.core

import android.os.Build
import android.util.Log

/**
 * Perfil de compatibilidade de hardware para dispositivos Android.
 */
data class DeviceProfile(
    val isCubotKingKongXPro: Boolean,
    val preferredSampleRate: Int,
    val preferredScoBufferMultiplier: Int,
    val supportsMediaTekDuraSpeed: Boolean,
    val recommendedHighPassCutoff: Float,
    val profileName: String
)

/**
 * DeviceCompatibilityManager — Camada de abstração e isolamento de particularidades de hardware.
 * 
 * Especializado para o Cubot KingKong X Pro (MediaTek Dimensity 8200, Android 14 API 34),
 * garantindo taxa ótima de amostragem mSBC (16.000 Hz), buffers anti-underrun para o driver MediaTek
 * e diretrizes de imunidade ao DuraSpeed/Battery Optimization.
 */
object DeviceCompatibilityManager {

    private const val TAG = "BTMIC_COMPAT"

    val currentProfile: DeviceProfile by lazy {
        detectProfile()
    }

    private fun detectProfile(): DeviceProfile {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()

        val isCubot = manufacturer.contains("cubot") || model.contains("kingkong") || model.contains("x pro")
        val isMediaTek = hardware.contains("mt") || hardware.contains("dimensity") || Build.SOC_MODEL.orEmpty().lowercase().contains("dimensity")

        Log.d(TAG, "Detectando hardware: Manufacturer=$manufacturer Model=$model Hardware=$hardware isCubot=$isCubot isMediaTek=$isMediaTek")

        return if (isCubot) {
            Log.i(TAG, "Perfil de hardware ativado: Cubot KingKong X Pro (MediaTek Dimensity 8200)")
            DeviceProfile(
                isCubotKingKongXPro = true,
                preferredSampleRate = 16000, // Nativo mSBC no driver MediaTek SCO
                preferredScoBufferMultiplier = 2, // Previne buffer underruns no chipset Dimensity
                supportsMediaTekDuraSpeed = true,
                recommendedHighPassCutoff = 120.0f, // Ponto de corte ideal para microfones de capacete
                profileName = "Cubot KingKong X Pro (MediaTek Dimensity 8200)"
            )
        } else {
            Log.i(TAG, "Perfil de hardware ativado: Android Genérico")
            DeviceProfile(
                isCubotKingKongXPro = false,
                preferredSampleRate = 16000,
                preferredScoBufferMultiplier = 1,
                supportsMediaTekDuraSpeed = false,
                recommendedHighPassCutoff = 120.0f,
                profileName = "Perfil Genérico Android (${Build.MANUFACTURER} ${Build.MODEL})"
            )
        }
    }
}
