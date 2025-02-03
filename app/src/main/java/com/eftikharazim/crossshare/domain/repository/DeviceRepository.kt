package com.eftikharazim.crossshare.domain.repository

import com.eftikharazim.crossshare.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun getDiscoveredDevices(): Flow<List<Device>>
    suspend fun registerDevice(port: Int)
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun tearDown()
}