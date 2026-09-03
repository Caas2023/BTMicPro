# ==============================================================================
# Regras do ProGuard / R8 para o BT Mic Pro V2
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Twilio AudioSwitch
# Mantém as classes e membros do AudioSwitch responsáveis pelo roteamento de áudio
# ------------------------------------------------------------------------------
-keep class com.twilio.audioswitch.** { *; }
-dontwarn com.twilio.audioswitch.**

# NOTA: DeepFilterNet removido - não usado no projeto atual (era código futuro)
# Se adicionar de novo, descomentar:
# -keep class io.github.nicmord.deepfilternet.** { *; }
# -dontwarn io.github.nicmord.deepfilternet.**

# Preserva todos os métodos nativos (JNI) para evitar perda de vínculo com bibliotecas C/Rust (.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 2. Telecom ConnectionService (Chamada Fantasma)
# ------------------------------------------------------------------------------
-keep class com.btmicpro.telecom.** { *; }

# ------------------------------------------------------------------------------
# 3. Kotlin Coroutines e Flow
# ------------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# 4. Jetpack Compose e Lifecycle
# ------------------------------------------------------------------------------
-keepclassmembers class androidx.compose.runtime.Recomposer {
    <fields>;
    <methods>;
}
-keep class androidx.lifecycle.** { *; }

# ------------------------------------------------------------------------------
# 5. AudioTrack keep-alive não pode ser removido pelo R8
# ------------------------------------------------------------------------------
-keep class com.btmicpro.core.SilentAudioKeeper { *; }
-keep class com.btmicpro.core.BluetoothAudioRouter { *; }
