package com.besenok.smarthome.data.remote.iobroker.model

import kotlinx.serialization.Serializable

@Serializable
data class IobrokerRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val id: Int,
    val params: List<String> = emptyList()
)
