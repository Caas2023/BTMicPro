package com.btmicpro.core

import android.os.Build
import android.util.Log

/**
 * Interface/Classe base de perfil de dispositivo com parâmetros observáveis e configuráveis
 * (Itens 11 e 46 do Prompt Master).
 */
data class DeviceProfile(
    val profileName: String,
    val isCubotKingKongXPro: Boolean,
    val preferredSampleRate: Int = 16000,
    val preferredBufferSize: Int = 2,
    val useLegacyScoFallback: Boolean = false,
    val scoConnectionTimeout: Long = 10000L,
    val routingRetryDelay: Long = 500L,
    val supportsMediaTekDuraSpeed: Boolean = false
)

/**
 * Perfil dedicado para o Cubot KingKong X Pro (MediaTek Dimensity 8200, Android 14/15).
 * Contém apenas parâmetros observáveis e configuráveis, sem alegações infundadas sobre codecs nativos.
 */
object CubotKingKongXProProfile {
    fun create(): DeviceProfile = DeviceProfile(
        profileName = "Cubot KingKong X Pro (MediaTek Dimensity 8200)",
        isCubotKingKongXPro = true,
        preferredSampleRate = 16000,
        preferredBufferSize = 2,
        useLegacyScoFallback = false,
        scoConnectionTimeout = 10000L,
        routingRetryDelay = 500L,
        supportsMediaTekDuraSpeed = true
    )
}

/**
 * Perfil genérico universal para outros aparelhos Android.
 */
object GenericDeviceProfile {
    fun create(): DeviceProfile = DeviceProfile(
        profileName = "Perfil Universal Android (${Build.MANUFACTURER} ${Build.MODEL})",
        isCubotKingKongXPro = false,
        preferredSampleRate = 16000,
        preferredBufferSize = 1,
        useLegacyScoFallback = false,
        scoConnectionTimeout = 10000L,
        routingRetryDelay = 600L,
        supportsMediaTekDuraSpeed = false
    )
}

/**
 * DeviceCompatibilityManager — Camada de abstração e isolamento de particularidades de hardware.
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
        val isMediaTek = hardware.contains("mt") || hardware.contains("dimensity") || (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.orEmpty().lowercase().contains("dimensity"))

        Log.d(TAG, "Detectando hardware: Manufacturer=$manufacturer Model=$model Hardware=$hardware isCubot=$isCubot isMediaTek=$isMediaTek")

        return if (isCubot) {
            Log.i(TAG, "Perfil de hardware ativado: CubotKingKongXProProfile")
            CubotKingKongXProProfile.create()
        } else {
            Log.i(TAG, "Perfil de hardware ativado: GenericDeviceProfile")
            GenericDeviceProfile.create()
        }
    }
}
