package com.besenok.smarthome.data.remote.iobroker.model

import kotlinx.serialization.Serializable

@Serializable
data class IobrokerState(
    val id: String,
    val `val`: String?,
    val ack: Boolean,
    val ts: Long,
    val lc: Long,
    val from: String?,
    val q: Int? = 0,
    val user: String? = null
)
