package com.btmicpro.service

enum class BtMicServiceStartMode {
    ROUTE_ONLY,
    ROUTE_WITH_MICROPHONE;

    companion object {
        const val ACTION_ROUTE_ONLY = "com.btmicpro.action.ROUTE_ONLY"
        const val ACTION_ROUTE_WITH_MICROPHONE = "com.btmicpro.action.ROUTE_WITH_MICROPHONE"

        fun fromAction(action: String?): BtMicServiceStartMode = when (action) {
            ACTION_ROUTE_WITH_MICROPHONE -> ROUTE_WITH_MICROPHONE
            else -> ROUTE_ONLY
        }
    }
}
