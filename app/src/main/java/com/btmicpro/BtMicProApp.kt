package com.btmicpro

import android.app.Application
import android.util.Log

/**
 * Ponto de entrada da aplicação Android BT Mic Pro.
 */
class BtMicProApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.btmicpro.core.AppLogger.init(this)
        Log.d(TAG, "BT Mic Pro inicializado com sucesso.")
    }

    companion object {
        private const val TAG = "BtMicProApp"
    }
}
