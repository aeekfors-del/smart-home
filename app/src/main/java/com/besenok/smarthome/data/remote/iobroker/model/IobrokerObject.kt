package com.besenok.smarthome.data.remote.iobroker.model

import kotlinx.serialization.Serializable

@Serializable
data class IobrokerObject(
    val _id: String,
    val type: String,
    val common: CommonInfo,
    val native: Map<String, String> = emptyMap(),
    val parent: String? = null,
    val children: List<String> = emptyList()
) {
    @Serializable
    data class CommonInfo(
        val name: String,
        val type: String,
        val role: String,
        val desc: String? = null,
        val unit: String? = null,
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        val states: Map<String, String>? = null
    )
}
