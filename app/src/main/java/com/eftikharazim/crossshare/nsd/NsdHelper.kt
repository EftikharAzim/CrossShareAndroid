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
    companion object {
        private const val TAG = "NsdHelper"
        private const val SERVICE_TYPE = "_fileshare._tcp."
    }

    interface OnDeviceDiscoveredListener {
        fun onDeviceDiscovered(deviceName: String, hostAddress: String, port: Int)
        fun onDeviceLost(deviceName: String)
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val handlerThread = HandlerThread("NsdThread").apply { 
        start()
        Log.d(TAG, "NSD handler thread started")
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerService(port: Int) {
        Log.i(TAG, "Registering NSD service on port: $port")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Android-${Build.MODEL}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        Log.d(TAG, "Created service info - Name: ${serviceInfo.serviceName}, Type: $SERVICE_TYPE")

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service registered successfully - Name: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Service registration failed - Name: ${serviceInfo.serviceName}, Error code: $code")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service unregistered - Name: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Service unregistration failed - Name: ${serviceInfo.serviceName}, Error code: $code")
            }
        }

        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            registrationListener
        )
    }

    fun discoverDevices() {
        Log.i(TAG, "Starting device discovery for service type: $SERVICE_TYPE")
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for service type: $serviceType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found - Name: ${service.serviceName}, Type: ${service.serviceType}")
                resolveService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost - Name: ${service.serviceName}")
                listener.onDeviceLost(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped for service type: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, code: Int) {
                Log.e(TAG, "Start discovery failed - Service type: $serviceType, Error code: $code")
            }

            override fun onStopDiscoveryFailed(serviceType: String, code: Int) {
                Log.e(TAG, "Stop discovery failed - Service type: $serviceType, Error code: $code")
            }
        }

        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )
    }

    @SuppressLint("NewApi")
    private fun resolveService(service: NsdServiceInfo) {
        Log.d(TAG, "Attempting to resolve service - Name: ${service.serviceName}")
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(service: NsdServiceInfo) {
                Log.i(TAG, "Service resolved - Name: ${service.serviceName}, Host: ${service.host}, Port: ${service.port}")
                handleResolvedService(service)
            }

            override fun onResolveFailed(service: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Service resolution failed - Name: ${service.serviceName}, Error code: $code")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsdManager.resolveService(service, resolveListener)
        } else {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(service, resolveListener)
        }
    }

    private fun handleResolvedService(service: NsdServiceInfo) {
        val hostAddress = service.host?.hostAddress
        if (hostAddress == null) {
            Log.w(TAG, "Resolved service has no host address - Name: ${service.serviceName}")
            return
        }

        if (!service.serviceName.startsWith("Android-")) {
            Log.d(TAG, "Processing non-Android service - Name: ${service.serviceName}, Host: $hostAddress, Port: ${service.port}")
            listener.onDeviceDiscovered(service.serviceName, hostAddress, service.port)
        } else {
            Log.v(TAG, "Skipping Android service - Name: ${service.serviceName}")
        }
    }

    fun stopDiscovery() {
        Log.i(TAG, "Stopping service discovery")
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
                Log.d(TAG, "Service discovery stopped successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service discovery: ${e.message}", e)
            }
        }
        discoveryListener = null
    }

    fun tearDown() {
        Log.i(TAG, "Tearing down NsdHelper")
        registrationListener?.let { 
            nsdManager.unregisterService(it)
            Log.d(TAG, "Service unregistered")
        }
        discoveryListener?.let { 
            nsdManager.stopServiceDiscovery(it)
            Log.d(TAG, "Discovery stopped")
        }
        handlerThread.quit()
        Log.d(TAG, "Handler thread terminated")
    }
}