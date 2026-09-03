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
 * V2.2 - Fix WhatsApp: Não usa mais phoneCall para não ser detectado como ligação.
 * Usa apenas microphone|connectedDevice + MODE_NORMAL + setCommunicationDevice
 */
class BtMicService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var audioRouter: BluetoothAudioRouter
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Criando BtMicService V2.2 - Sem phoneCall (fix WhatsApp)")

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
            // FIX: Removido PHONE_CALL para WhatsApp não detectar como ligação
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
            is RouterState.RoutingVerified -> {
                buildNotification(
                    title = "🎧 Microfone Bluetooth Verificado",
                    content = "Pronto para WhatsApp: ${state.device.name} (${state.sampleRate}Hz)"
                )
            }
            is RouterState.ScoActive -> {
                buildNotification(
                    title = "🎧 Canal SCO Ativo",
                    content = "Canal de voz conectado: ${state.device.name}"
                )
            }
            is RouterState.CommunicationDeviceSelected -> {
                buildNotification(
                    title = "🎧 Dispositivo Selecionado",
                    content = "Comunicação vinculada: ${state.device.name}"
                )
            }
            is RouterState.AudioDeviceAvailable -> {
                buildNotification(
                    title = "🎧 Fone de Áudio Detectado",
                    content = "Configurando rotas para: ${state.device.name}"
                )
            }
            is RouterState.BluetoothConnected -> {
                buildNotification(
                    title = "🟡 Bluetooth Conectado",
                    content = "Preparando canal de voz: ${state.device.name}"
                )
            }
            is RouterState.Recovering -> {
                buildNotification(
                    title = "🔄 Reconectando Fone...",
                    content = "Tentativa ${state.attempt} de recuperação do canal"
                )
            }
            is RouterState.RoutingLost -> {
                buildNotification(
                    title = "⚠️ Conexão Perdida",
                    content = state.reason
                )
            }
            is RouterState.RoutingActive -> {
                buildNotification(
                    title = getString(R.string.notification_title_active),
                    content = "Conectado a: ${state.device.name} (Ativo)"
                )
            }
            is RouterState.WaitingDevice -> {
                buildNotification(
                    title = getString(R.string.notification_title_waiting),
                    content = getString(R.string.notification_desc_waiting)
                )
            }
            is RouterState.Error -> {
                buildNotification(
                    title = "⚠️ Alerta de Áudio Bluetooth",
                    content = state.message
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
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                pendingStop
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        Log.d(TAG, "Destruindo BtMicService")
        com.btmicpro.core.RouterStateHolder.updateServiceRunning(false)
        audioRouter.stopRouting()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BtMicService"
        const val CHANNEL_ID = "bt_mic_router_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.btmicpro.ACTION_STOP_SERVICE"

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
