package com.eftikharazim.crossshare.data.repository

import android.content.Context
import com.eftikharazim.crossshare.domain.model.Device
import com.eftikharazim.crossshare.domain.repository.DeviceRepository
import com.eftikharazim.crossshare.nsd.NsdHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class DeviceRepositoryImpl(
    private val context: Context
) : DeviceRepository {
    private var nsdHelper: NsdHelper? = null

    override fun getDiscoveredDevices(): Flow<List<Device>> = callbackFlow {
        val deviceList = mutableListOf<Device>()
        
        val listener = object : NsdHelper.OnDeviceDiscoveredListener {
            override fun onDeviceDiscovered(deviceName: String, hostAddress: String, port: Int) {
                val device = Device(
                    id = "$deviceName-$hostAddress-$port",
                    name = deviceName,
                    host = hostAddress,
                    port = port
                )
                if (!deviceList.any { it.id == device.id }) {
                    deviceList.add(device)
                    trySend(deviceList.toList())
                }
            }

            override fun onDeviceLost(deviceName: String) {
                deviceList.removeAll { it.name == deviceName }
                trySend(deviceList.toList())
            }
        }

        nsdHelper = NsdHelper(context, listener)
        
        awaitClose {
            nsdHelper?.tearDown()
            nsdHelper = null
        }
    }

    override suspend fun registerDevice(port: Int) {
        nsdHelper?.registerService(port)
    }

    override suspend fun startDiscovery() {
        nsdHelper?.discoverDevices()
    }

    override suspend fun stopDiscovery() {
        nsdHelper?.stopDiscovery()
    }

    override suspend fun tearDown() {
        nsdHelper?.tearDown()
        nsdHelper = null
    }
}