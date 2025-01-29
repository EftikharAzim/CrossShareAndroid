package com.eftikharazim.crossshare

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.eftikharazim.crossshare.databinding.ActivityMainBinding
import com.eftikharazim.crossshare.filetransfer.FileTransfer
import com.eftikharazim.crossshare.nsd.NsdHelper

class MainActivity : AppCompatActivity(), NsdHelper.OnDeviceDiscoveredListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var nsdHelper: NsdHelper
    private lateinit var fileTransfer: FileTransfer
    private val discoveredDevices = mutableListOf<DeviceInfo>()
    private lateinit var adapter: DeviceAdapter
    private var selectedDevice: DeviceInfo? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sendFileToSelectedDevice(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
        setupRecyclerView()
        setupFileTransfer()
        startNsd()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 0
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = DeviceAdapter(discoveredDevices) { device ->
            selectedDevice = device
            openFilePicker()
        }

        binding.discoveredDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupFileTransfer() {
        fileTransfer = FileTransfer(this)
        fileTransfer.setTransferListener(object : FileTransfer.TransferListener {
            override fun onProgress(percent: Int) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, "Progress: $percent%", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, "Transfer successful!", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, "Error: $message", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        // Start receiving files on port 1234
//        fileTransfer.startServer(1234)
        fileTransfer.startServer(8080)
    }

    private fun startNsd() {
        nsdHelper = NsdHelper(this, this)
//        nsdHelper.registerService(1234)
        nsdHelper.registerService(8080)
        nsdHelper.discoverDevices()
    }

    override fun onDeviceDiscovered(deviceName: String, hostAddress: String, port: Int) {
        runOnUiThread {
            val newDevice = DeviceInfo(deviceName, hostAddress, port)
            if (!discoveredDevices.any { it.host == hostAddress }) {
                discoveredDevices.add(newDevice)
                adapter.notifyItemInserted(discoveredDevices.size - 1)
            }
        }
    }

    override fun onDeviceLost(deviceName: String) {
        runOnUiThread {
            discoveredDevices.removeAll { it.name == deviceName }
            adapter.notifyDataSetChanged()
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    private fun sendFileToSelectedDevice(fileUri: Uri) {
        selectedDevice?.let { device ->
            fileTransfer.sendFile(device.host, device.port, fileUri)
        } ?: run {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        nsdHelper.tearDown()
        super.onDestroy()
    }

    data class DeviceInfo(val name: String, val host: String, val port: Int)
}