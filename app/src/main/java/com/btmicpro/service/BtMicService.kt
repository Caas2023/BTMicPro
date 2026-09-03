package com.btmicpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.btmicpro.MainActivity
import com.btmicpro.R
import com.btmicpro.core.BluetoothAudioRouter
import com.btmicpro.core.RouterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BtMicService — Foreground Service de Controle e Estabilização da Rota de Comunicação Bluetooth V4.
 *
 * Responsabilidade estrita (Item 51 do Prompt Master):
 * - Manter a rota de comunicação ativa em segundo plano sem transformar o app em gravador.
 * - Monitorar alterações no hardware e acionar a autorrecuperação.
 * - Exibir notificação com status real e honesto da rota de áudio.
 */
class BtMicService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var audioRouter: BluetoothAudioRouter
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Criando BtMicService V4 — Roteamento bidirecional WhatsApp ↔ Intercom")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        audioRouter = BluetoothAudioRouter(this, serviceScope)

        serviceScope.launch {
            audioRouter.routerState.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Ação de parada recebida. Encerrando BtMicService.")
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(
            title = getString(R.string.notification_title_waiting),
            content = getString(R.string.notification_desc_waiting)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, initialNotification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        audioRouter.startRouting()
        com.btmicpro.core.RouterStateHolder.updateServiceRunning(true)

        return START_STICKY
    }

    private fun updateNotification(state: RouterState) {
        val notification = when (state) {
            is RouterState.RouteReady -> {
                buildNotification(
                    title = "🎧 BT Mic Pro — Rota Pronta",
                    content = "Intercom: ${state.device.name} • Entrada e Saída ativas"
                )
            }
            is RouterState.RoutingVerified -> {
                buildNotification(
                    title = "🎧 BT Mic Pro — Rota Pronta",
                    content = "Intercom: ${state.device.name} • Canal verificado"
                )
            }
            is RouterState.OutputAvailable -> {
                buildNotification(
                    title = "🎧 Saída Bluetooth Pronta",
                    content = "Intercom: ${state.device.name} • Preparando microfone"
                )
            }
            is RouterState.InputAvailable -> {
                buildNotification(
                    title = "🎧 Microfone Bluetooth Pronto",
                    content = "Intercom: ${state.device.name} • Preparando fone"
                )
            }
            is RouterState.CommunicationDeviceSelected -> {
                buildNotification(
                    title = "🎧 Dispositivo Selecionado",
                    content = "Intercom: ${state.device.name} • Ativando canal"
                )
            }
            is RouterState.CommunicationDeviceAvailable, is RouterState.AudioDeviceAvailable -> {
                val name = if (state is RouterState.CommunicationDeviceAvailable) state.device.name else (state as RouterState.AudioDeviceAvailable).device.name
                buildNotification(
                    title = "🎧 Dispositivo Identificado",
                    content = "Intercom: $name • Vinculando ao sistema"
                )
            }
            is RouterState.BluetoothConnected -> {
                buildNotification(
                    title = "🟡 Bluetooth Conectado",
                    content = "Intercom: ${state.device.name} • Aguardando canal de voz"
                )
            }
            is RouterState.Recovering -> {
                buildNotification(
                    title = "🔄 Reconectando Rota...",
                    content = "Tentativa ${state.attempt} de restabelecimento do canal"
                )
            }
            is RouterState.RouteLost -> {
                buildNotification(
                    title = "⚠️ Rota de Comunicação Perdida",
                    content = state.reason
                )
            }
            is RouterState.RoutingLost -> {
                buildNotification(
                    title = "⚠️ Rota Perdida",
                    content = state.reason
                )
            }
            is RouterState.Error -> {
                buildNotification(
                    title = "⚠️ Alerta de Áudio Bluetooth",
                    content = state.message
                )
            }
            is RouterState.WaitingDevice -> {
                buildNotification(
                    title = getString(R.string.notification_title_waiting),
                    content = getString(R.string.notification_desc_waiting)
                )
            }
            is RouterState.RoutingActive -> {
                buildNotification(
                    title = "🎧 Intercom Ativo",
                    content = "Conectado a: ${state.device.name}"
                )
            }
            is RouterState.ScoActive -> {
                buildNotification(
                    title = "🎧 Canal SCO Ativo",
                    content = "Intercom: ${state.device.name}"
                )
            }
            RouterState.Disconnected, RouterState.Inactive -> return
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BtMicService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingOpenApp)
            .addAction(R.drawable.ic_launcher, getString(R.string.notification_action_stop), pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destruindo BtMicService V4 — Limpando rotas e recursos")
        audioRouter.stopRouting()
        com.btmicpro.core.RouterStateHolder.updateServiceRunning(false)
        com.btmicpro.core.RouterStateHolder.updateState(RouterState.Disconnected)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BtMicService"
        private const val CHANNEL_ID = "bt_mic_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.btmicpro.action.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, BtMicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BtMicService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
