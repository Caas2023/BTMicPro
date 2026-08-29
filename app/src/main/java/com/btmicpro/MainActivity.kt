package com.btmicpro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.btmicpro.ui.MainScreen
import com.btmicpro.ui.MainViewModel
import com.btmicpro.ui.theme.BTMicProTheme

/**
 * Activity principal do aplicativo BT Mic Pro.
 * Gerencia a requisição de permissões de sistema e renderiza a interface Jetpack Compose.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Lançador de Múltiplas Permissões em Tempo de Execução
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else true

        if (recordAudioGranted && bluetoothGranted) {
            Toast.makeText(this, "Permissões concedidas com sucesso!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Permissões de microfone e Bluetooth são necessárias para o funcionamento correto.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verifica e requisita permissões necessárias
        requestRequiredPermissions()

        setContent {
            BTMicProTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    /**
     * Requisita permissões em tempo de execução para Android 6.0 até Android 15.
     */
    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Permissão de Microfone
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // 2. Permissão de Bluetooth Connect (Android 12+ / API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        // 3. Permissão de Notificações (Android 13+ / API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
