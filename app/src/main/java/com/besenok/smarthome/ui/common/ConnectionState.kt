package com.besenok.smarthome.ui.common

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val sessionId: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
