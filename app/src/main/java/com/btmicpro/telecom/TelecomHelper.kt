package com.btmicpro.telecom

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * Helper para registrar PhoneAccount e controlar a chamada fantasma.
 * A chamada fantasma faz o sistema acreditar que existe chamada ativa,
 * o que força o AudioManager a manter SCO + MODE_IN_COMMUNICATION estável
 * para QUALQUER app (WhatsApp, Telegram, etc).
 */
object TelecomHelper {

    private const val TAG = "TelecomHelper"
    private const val ACCOUNT_ID = "bt_mic_pro_fake_call_account"
    private const val ACCOUNT_LABEL = "BT Mic Pro"

    private fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val component = ComponentName(context, FakeCallConnectionService::class.java)
        return PhoneAccountHandle(component, ACCOUNT_ID)
    }

    /**
     * Registra o PhoneAccount auto-gerenciado. Deve ser chamado 1x no onCreate do Service/App.
     * Requer permissão MANAGE_OWN_CALLS.
     */
    fun registerPhoneAccount(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = getPhoneAccountHandle(context)

            // Se já registrado, não re-registra
            val existing = telecomManager.getPhoneAccount(handle)
            if (existing != null && existing.isEnabled) {
                Log.d(TAG, "PhoneAccount já registrado e ativo")
                return
            }

            val phoneAccount = PhoneAccount.builder(handle, ACCOUNT_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED or PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING)
                .build()

            telecomManager.registerPhoneAccount(phoneAccount)
            Log.d(TAG, "PhoneAccount registrado com sucesso: $handle")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão MANAGE_OWN_CALLS não concedida", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar PhoneAccount", e)
        }
    }

    /**
     * Inicia a chamada fantasma. Após isso, o AudioManager manterá SCO estável.
     * Retorna true se conseguiu iniciar.
     */
    fun startFakeCall(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = getPhoneAccountHandle(context)

            // Garante que está registrado
            registerPhoneAccount(context)

            val account = telecomManager.getPhoneAccount(handle)
            if (account == null || !account.isEnabled) {
                Log.w(TAG, "PhoneAccount não está habilitado. Usuário precisa habilitar em Configurações > Apps > Telefone padrão? Tentando mesmo assim...")
                // Em SELF_MANAGED não precisa ser o default dialer, mas precisa estar registrado
            }

            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                // Extras para indicar que é chamada de voz
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            }

            // URI fantasma - não disca de verdade, apenas ativa o ConnectionService
            val fakeUri = Uri.fromParts("tel", "0000000000", null)
            telecomManager.placeCall(fakeUri, extras)
            Log.d(TAG, "Fake call iniciada via placeCall")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Sem permissão para placeCall - MANAGE_OWN_CALLS necessário", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar fake call", e)
            false
        }
    }

    /**
     * Encerra a chamada fantasma.
     */
    fun endFakeCall() {
        try {
            FakeCallConnectionService.disconnectActiveCall()
            Log.d(TAG, "Fake call encerrada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao encerrar fake call", e)
        }
    }

    /**
     * Limpa o PhoneAccount (opcional, ao desinstalar).
     */
    fun unregisterPhoneAccount(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = getPhoneAccountHandle(context)
            telecomManager.unregisterPhoneAccount(handle)
            Log.d(TAG, "PhoneAccount removido")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover PhoneAccount", e)
        }
    }
}
