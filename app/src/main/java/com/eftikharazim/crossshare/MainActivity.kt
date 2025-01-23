package com.eftikharazim.crossshare

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), NsdHelper.OnDeviceDiscoveredListener {
    private lateinit var nsdHelper: NsdHelper
    private val discoveredDevices = mutableListOf<String>()
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request permissions (Android 10+)
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)

        // Setup RecyclerView
        adapter = DeviceAdapter(discoveredDevices)
        findViewById<RecyclerView>(R.id.discovered_devices).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // Start NSD
        nsdHelper = NsdHelper(this, this)
        nsdHelper.registerService(1234)
        nsdHelper.discoverDevices()
    }

    override fun onDeviceDiscovered(deviceName: String, hostAddress: String) {
        runOnUiThread {
            discoveredDevices.add("$deviceName ($hostAddress)")
            adapter.notifyItemInserted(discoveredDevices.size - 1)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onDeviceLost(deviceName: String) {
        runOnUiThread {
            discoveredDevices.removeIf { it.startsWith(deviceName) }
            adapter.notifyDataSetChanged()
        }
    }

    override fun onPause() {
        super.onPause()
        nsdHelper.tearDown() // Unregister service when app goes to background
    }

    override fun onDestroy() {
        nsdHelper.tearDown()
        super.onDestroy()
    }
}