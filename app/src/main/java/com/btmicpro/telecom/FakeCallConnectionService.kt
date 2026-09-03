package com.btmicpro.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * ConnectionService fantasma que simula uma chamada ativa no sistema.
 * Quando ativa, o Android acredita que existe uma chamada em andamento e
 * força TODO app (WhatsApp, Telegram, etc) a usar o microfone SCO Bluetooth.
 *
 * Técnica usada por apps profissionais de roteamento BT para manter
 * "sempre em chamada" sem precisar de chamada real.
 */
class FakeCallConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "Criando FakeConnection (simulacao de chamada)")
        val connection = FakeConnection()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName("BT Mic Pro", TelecomManager.PRESENTATION_ALLOWED)
        // Marca como ativa imediatamente - é isso que engana o AudioManager
        connection.setActive()
        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "Criando FakeConnection incoming")
        val connection = FakeConnection()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setActive()
        activeConnection = connection
        return connection
    }

    private inner class FakeConnection : Connection() {
        init {
            // Capacidades que simulam chamada de voz real
            connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_HOLD
            audioModeIsVoip = false // false = usa modo telefonia tradicional (SCO garantido)
            setInitialized()
        }

        override fun onAnswer() {
            setActive()
        }

        override fun onDisconnect() {
            setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
            destroy()
            activeConnection = null
        }

        override fun onAbort() {
            setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.CANCELED))
            destroy()
            activeConnection = null
        }

        override fun onHold() {
            setOnHold()
        }

        override fun onUnhold() {
            setActive()
        }
    }

    companion object {
        private const val TAG = "FakeCallService"
        var activeConnection: Connection? = null
            private set

        fun disconnectActiveCall() {
            try {
                activeConnection?.setDisconnected(
                    android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL)
                )
                activeConnection?.destroy()
                activeConnection = null
                Log.d(TAG, "FakeConnection desconectada")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao desconectar FakeConnection", e)
            }
        }
    }
}
