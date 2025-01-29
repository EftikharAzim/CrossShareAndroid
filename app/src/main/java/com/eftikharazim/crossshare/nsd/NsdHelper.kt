package com.eftikharazim.crossshare.nsd

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.HandlerThread
import android.util.Log

class NsdHelper(
    private val context: Context,
    private val listener: OnDeviceDiscoveredListener
) {
    interface OnDeviceDiscoveredListener {
        fun onDeviceDiscovered(deviceName: String, hostAddress: String, port: Int) // 3 parameters
        fun onDeviceLost(deviceName: String)
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_fileshare._tcp."
    private val handlerThread = HandlerThread("NsdThread").apply { start() }

    // Declare listeners as class properties
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Android-${Build.MODEL}"
            serviceType = this@NsdHelper.serviceType
            setPort(port)
        }

        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.d("NSD", "Registered: ${serviceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, code: Int) {
                    Log.e("NSD", "Registration failed: $code")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.e("NSD", "Unregistered: ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, code: Int) {
                    Log.e("NSD", "Unregistration failed: $code")
                }
            }
        )
    }

    fun discoverDevices() {
        nsdManager.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d("NSD", "Discovery started")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d("NSD", "Found service: ${service.serviceName}")
                    resolveService(service)
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    listener.onDeviceLost(service.serviceName)
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d("NSD", "Discovery stopped")
                }

                override fun onStartDiscoveryFailed(serviceType: String, code: Int) {
                    Log.e("NSD", "Discovery failed: $code")
                }

                override fun onStopDiscoveryFailed(serviceType: String, code: Int) {
                    Log.e("NSD", "Stop discovery failed: $code")
                }
            }
        )
    }

    @SuppressLint("NewApi")
    private fun resolveService(service: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(service: NsdServiceInfo) {
                    handleResolvedService(service)
                }

                override fun onResolveFailed(service: NsdServiceInfo, code: Int) {
                    Log.e("NSD", "Resolve failed: $code")
                }
            })
        } else {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(service: NsdServiceInfo) {
                    handleResolvedService(service)
                }

                override fun onResolveFailed(service: NsdServiceInfo, code: Int) {
                    Log.e("NSD", "Resolve failed: $code")
                }
            })
        }
    }

    private fun handleResolvedService(service: NsdServiceInfo) {
        val hostAddress = service.host?.hostAddress ?: return
        // Skip Android devices (they start with "Android-")
        if (!service.serviceName.startsWith("Android-")) {
            listener.onDeviceDiscovered(service.serviceName, hostAddress, service.port)
        }
    }

    fun tearDown() {
        registrationListener?.let { nsdManager.unregisterService(it) }
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        handlerThread.quit()
    }
}