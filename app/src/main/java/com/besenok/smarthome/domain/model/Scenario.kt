package com.besenok.smarthome.domain.model

data class Scenario(
    val id: String,
    val name: String,
    val description: String?,
    val trigger: String?,
    val actions: List<String>,
    val enabled: Boolean = true,
    val lastExecuted: Long? = null
)
