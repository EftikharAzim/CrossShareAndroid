package com.eftikharazim.crossshare.domain.model

data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int
)