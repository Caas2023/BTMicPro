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
import com.btmicpro.core.BluetoothRoutingEngine
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

    private lateinit var routingEngine: BluetoothRoutingEngine
    private lateinit var notificationManager: NotificationManager
    private lateinit var dualVolumeManager: com.btmicpro.core.DualVolumeManager
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Criando BtMicService V4 — Roteamento bidirecional WhatsApp ↔ Intercom")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        dualVolumeManager = com.btmicpro.core.DualVolumeManager.getInstance(this)
        dualVolumeManager.startMonitoring()

        routingEngine = BluetoothRoutingEngine(this, serviceScope)

        serviceScope.launch {
            routingEngine.routerState.collect { state ->
                com.btmicpro.core.RouterStateHolder.updateState(state)
                com.btmicpro.core.AppLogger.i(TAG, "Notificação de rota atualizada para: ${state.javaClass.simpleName}")
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SERVICE) {
            com.btmicpro.core.AppLogger.i(TAG, "Ação de parada recebida no serviço. Encerrando BtMicService.")
            com.btmicpro.core.RouterStateHolder.updateServiceRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val startMode = BtMicServiceStartMode.fromAction(action)
        val initialNotification = buildNotification(
            title = getString(R.string.notification_title_waiting),
            content = getString(R.string.notification_desc_waiting)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    if (startMode == BtMicServiceStartMode.ROUTE_WITH_MICROPHONE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    foregroundTypes
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }

            com.btmicpro.core.AppLogger.i(TAG, "BtMicService em primeiro plano: modo $startMode")
            com.btmicpro.core.RouterStateHolder.activeEngine = routingEngine
            routingEngine.startEngine()
            // Sem AudioRecord próprio: o WhatsApp é o dono exclusivo da captura.
            // Um segundo gravador competindo pelo microfone derrubava o SCO no meio
            // da nota de voz. O canal é segurado pelo keep-alive de silêncio.
            com.btmicpro.core.RouterStateHolder.updateServiceRunning(true)
        } catch (e: RuntimeException) {
            com.btmicpro.core.AppLogger.e(TAG, "Android recusou o início do serviço de rota", e)
            com.btmicpro.core.RouterStateHolder.updateState(
                RouterState.Error("Não foi possível iniciar o serviço em segundo plano. Abra o app e tente novamente.")
            )
            com.btmicpro.core.RouterStateHolder.updateServiceRunning(false)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun updateNotification(state: RouterState) {
        val notification = when (state) {
            is RouterState.RouteReady -> {
                buildNotification(
                    title = "🎧 BT Mic Pro — Rota Pronta",
                    content = "Intercom: ${state.device.name} • Rota preparada; valide no WhatsApp"
                )
            }
            is RouterState.RoutingVerified -> {
                buildNotification(
                    title = "🎧 BT Mic Pro — Rota Pronta",
                    content = "Intercom: ${state.device.name} • Canal verificado"
                )
            }
            is RouterState.AudioConnected -> {
                buildNotification(
                    title = "🎧 Canal de Áudio HFP Conectado",
                    content = "Intercom: ${state.device.name} • Canal ativo no capacete"
                )
            }
            is RouterState.AudioConnecting -> {
                buildNotification(
                    title = "🎧 Negociando Canal de Áudio",
                    content = "Intercom: ${state.device.name} • Estabelecendo áudio HFP"
                )
            }
            is RouterState.RouteDegraded -> {
                buildNotification(
                    title = if (state.isMediaPlayback) "🎧 Reprodução de mídia" else "⚠️ Rota de Áudio Parcial",
                    content = "Intercom: ${state.device.name} • ${state.reason}"
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
            RouterState.Disconnected, RouterState.Inactive -> buildNotification(
                title = "BT Mic Pro — Aguardando intercom",
                content = "Canal de voz não conectado"
            )
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
            .setSmallIcon(R.drawable.ic_notification_btmic)
            .setContentIntent(pendingOpenApp)
            .addAction(R.drawable.ic_notification_stop, getString(R.string.notification_action_stop), pendingStop)
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
        com.btmicpro.core.AppLogger.i(TAG, "Destruindo BtMicService — Limpando rotas de áudio e recursos do SO")
        // BUG-12: Cancela o serviceScope ANTES de parar a engine.
        // A coroutine de coleta de routerState (linha 57) precisa ser cancelada
        // primeiro para evitar que tente atualizar a notificação durante o shutdown.
        // Sem isso, a engine publica RouterState.Disconnected via updateState(),
        // mas a coroutine de coleta pode já estar em estado inconsistente,
        // resultando em notificação persistente com estado errado.
        serviceScope.cancel()
        routingEngine.stopEngine()
        dualVolumeManager.stopMonitoring()
        com.btmicpro.core.RouterStateHolder.updateServiceRunning(false)
        com.btmicpro.core.RouterStateHolder.updateState(RouterState.Disconnected)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BtMicService"
        private const val CHANNEL_ID = "bt_mic_service_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.btmicpro.action.STOP_SERVICE"

        fun start(
            context: Context,
            mode: BtMicServiceStartMode = BtMicServiceStartMode.ROUTE_ONLY
        ): Boolean {
            val intent = Intent(context, BtMicService::class.java).apply {
                action = when (mode) {
                    BtMicServiceStartMode.ROUTE_ONLY -> BtMicServiceStartMode.ACTION_ROUTE_ONLY
                    BtMicServiceStartMode.ROUTE_WITH_MICROPHONE ->
                        BtMicServiceStartMode.ACTION_ROUTE_WITH_MICROPHONE
                }
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: RuntimeException) {
                com.btmicpro.core.AppLogger.e(TAG, "Falha ao solicitar início do serviço", e)
                com.btmicpro.core.RouterStateHolder.updateState(
                    RouterState.Error("O Android bloqueou o início automático. Abra o BT Mic Pro e ative novamente.")
                )
                com.btmicpro.core.RouterStateHolder.updateServiceRunning(false)
                false
            }
        }

        fun stop(context: Context) {
            // Parar um serviço inativo não deve criá-lo nem inicializar áudio/volumes.
            context.stopService(Intent(context, BtMicService::class.java))
        }
    }
}
