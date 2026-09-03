package com.btmicpro.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.btmicpro.receiver.BootReceiver
import com.btmicpro.ui.theme.AccentRed
import com.btmicpro.ui.theme.PrimaryNeon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper

/**
 * Botão flutuante OPCIONAL: 100% MAIOR (112dp), TODO VERDE / TODO VERMELHO.
 */
class FloatingButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var prefs: SharedPreferences? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isEnabledFlow = MutableStateFlow(false)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var lifecycleOwner: FloatingLifecycleOwner

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        prefs = getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val initialRunning = com.btmicpro.core.RouterStateHolder.isServiceRunning.value ||
                prefs!!.getBoolean(BootReceiver.KEY_ROUTER_ENABLED, false)
        isEnabledFlow.value = initialRunning

        // Sincronização bidirecional em tempo real com o botão principal do app
        serviceScope.launch {
            com.btmicpro.core.RouterStateHolder.isServiceRunning.collect { isRunning ->
                isEnabledFlow.value = isRunning
                prefs?.edit()?.putBoolean(BootReceiver.KEY_ROUTER_ENABLED, isRunning)?.apply()
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 300
        }

        lifecycleOwner = FloatingLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val isEnabled by isEnabledFlow.collectAsState()
                val bgColor = if (isEnabled) PrimaryNeon else AccentRed
                val iconTint = Color.White

                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable {
                            if (!isMoving) toggleRouter()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.HeadsetMic else Icons.Default.PowerSettingsNew,
                        contentDescription = if (isEnabled) "Desativar" else "Ativar",
                        tint = iconTint,
                        modifier = Modifier.size(39.dp)
                    )
                }
            }
        }

        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) isMoving = true
                    params!!.x = initialX - dx
                    params!!.y = initialY + dy
                    try { windowManager?.updateViewLayout(composeView, params) } catch (ignored: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isMoving) {
                        handler.postDelayed({ isMoving = false }, 100)
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = composeView
        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun toggleRouter() {
        val currentlyRunning = com.btmicpro.core.RouterStateHolder.isServiceRunning.value
        val newEnabled = !currentlyRunning
        isEnabledFlow.value = newEnabled
        prefs?.edit()?.putBoolean(BootReceiver.KEY_ROUTER_ENABLED, newEnabled)?.apply()
        com.btmicpro.core.RouterStateHolder.updateServiceRunning(newEnabled)

        if (newEnabled) {
            BtMicService.start(this)
            Toast.makeText(this, "MOTO MODE ligado", Toast.LENGTH_SHORT).show()
        } else {
            BtMicService.stop(this)
            Toast.makeText(this, "MOTO MODE desligado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { floatingView?.let { windowManager?.removeView(it) } } catch (ignored: Exception) {}
        floatingView = null
        if (::lifecycleOwner.isInitialized) {
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingButtonService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, FloatingButtonService::class.java)) } catch (ignored: Exception) {}
        }

        fun isOverlayGranted(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun requestOverlayPermission(context: Context) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (e: Exception) { }
        }
    }

    private class FloatingLifecycleOwner : ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) { lifecycleRegistry.handleLifecycleEvent(event) }
        fun performRestore(savedState: android.os.Bundle?) { controller.performRestore(savedState) }
    }
}

