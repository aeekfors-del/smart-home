package com.besenok.smarthome.data.remote.iobroker.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IobrokerResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: RpcError? = null,
    val id: Int? = null,
    val method: String? = null,
    val params: JsonElement? = null
) {
    @Serializable
    data class RpcError(
        val code: Int,
        val message: String
    )
}
