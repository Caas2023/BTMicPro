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
import com.btmicpro.core.BatteryOptimizationHelper
import com.btmicpro.ui.MainScreen
import com.btmicpro.ui.MainViewModel
import com.btmicpro.ui.theme.BTMicProTheme

/**
 * Activity principal do aplicativo BT Mic Pro.
 * V2: Whitelist de bateria + rechecagem de promo a cada 5h no onResume
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else true

        if (recordAudioGranted && bluetoothGranted) {
            Toast.makeText(this, "Permissões concedidas com sucesso!", Toast.LENGTH_SHORT).show()
            checkAndRequestBatteryOptimization()
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
        requestRequiredPermissions()
        setContent {
            BTMicProTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recheca promo: se fechou há 5h, volta a mostrar
        viewModel.onResumeCheckPromo()
    }

    private fun hasBasicPermissions(): Boolean {
        val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val btOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        return audioOk && btOk
    }

    private fun checkAndRequestBatteryOptimization() {
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            window.decorView.postDelayed({
                Toast.makeText(
                    this,
                    "Para funcionar na moto com tela desligada, libere o BT Mic Pro na próxima tela",
                    Toast.LENGTH_LONG
                ).show()
                BatteryOptimizationHelper.requestIgnoreBatteryOptimization(this)
            }, 1500)
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkAndRequestBatteryOptimization()
        }
    }
}
