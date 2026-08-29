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
 * Serviço em primeiro plano (Foreground Service) encarregado de manter o roteamento do microfone
 * Bluetooth ativo ininterruptamente em segundo plano para o WhatsApp e outros aplicativos.
 */
class BtMicService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var audioRouter: BluetoothAudioRouter
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Criando BtMicService (Foreground Service).")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        audioRouter = BluetoothAudioRouter(this, serviceScope)

        // Observa alterações no estado do roteador para atualizar a notificação dinamicamente
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

        // Inicia o serviço em primeiro plano com notificação inicial
        val initialNotification = buildNotification(
            title = getString(R.string.notification_title_waiting),
            content = getString(R.string.notification_desc_waiting)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Inicia o roteamento do microfone Bluetooth
        audioRouter.startRouting()

        return START_STICKY
    }

    /**
     * Atualiza o conteúdo da notificação conforme o estado do roteamento Bluetooth.
     */
    private fun updateNotification(state: RouterState) {
        val notification = when (state) {
            is RouterState.RoutingActive -> {
                buildNotification(
                    title = getString(R.string.notification_title_active),
                    content = "Conectado a: ${state.device.name} (Modo WhatsApp Ativo)"
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
            RouterState.Inactive -> return
        }

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Constrói a notificação persistente com ações de retorno ao app e parada.
     */
    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BtMicService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    /**
     * Cria o canal de notificações necessário para Android 8.0 (API 26) ou superior.
     */
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
        Log.d(TAG, "Destruindo BtMicService. Liberando recursos.")
        audioRouter.stopRouting()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BtMicService"
        const val CHANNEL_ID = "bt_mic_router_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.btmicpro.ACTION_STOP_SERVICE"

        /**
         * Inicia o serviço de roteamento em primeiro plano de forma compatível com todas as versões do Android.
         */
        fun start(context: Context) {
            val intent = Intent(context, BtMicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Para o serviço de roteamento.
         */
        fun stop(context: Context) {
            val intent = Intent(context, BtMicService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
