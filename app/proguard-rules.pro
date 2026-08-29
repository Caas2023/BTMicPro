# ==============================================================================
# Regras do ProGuard / R8 para o BT Mic Pro
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Twilio AudioSwitch
# Mantém as classes e membros do AudioSwitch responsáveis pelo roteamento de áudio
# ------------------------------------------------------------------------------
-keep class com.twilio.audioswitch.** { *; }
-dontwarn com.twilio.audioswitch.**

# ------------------------------------------------------------------------------
# 2. DeepFilterNet (Cancelamento de Ruído via IA)
# Preserva a biblioteca de processamento de áudio e as interfaces JNI/Nativas
# ------------------------------------------------------------------------------
-keep class io.github.nicmord.deepfilternet.** { *; }
-dontwarn io.github.nicmord.deepfilternet.**

# Preserva todos os métodos nativos (JNI) para evitar perda de vínculo com bibliotecas C/Rust (.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 3. Kotlin Coroutines e Flow
# ------------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# 4. Hilt / Dagger (Injeção de Dependências)
# ------------------------------------------------------------------------------
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn com.google.errorprone.annotations.**

# ------------------------------------------------------------------------------
# 5. Jetpack Compose e Lifecycle
# ------------------------------------------------------------------------------
-keepclassmembers class androidx.compose.runtime.Recomposer {
    <fields>;
    <methods>;
}
-keep class androidx.lifecycle.** { *; }

# ------------------------------------------------------------------------------
# 6. Android DataStore Preferences
# ------------------------------------------------------------------------------
-keep class androidx.datastore.** { *; }
