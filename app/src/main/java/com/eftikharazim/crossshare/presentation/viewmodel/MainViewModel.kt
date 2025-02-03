package com.eftikharazim.crossshare.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eftikharazim.crossshare.domain.model.Device
import com.eftikharazim.crossshare.domain.repository.DeviceRepository
import com.eftikharazim.crossshare.domain.repository.FileTransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val deviceRepository: DeviceRepository,
    private val fileTransferRepository: FileTransferRepository
) : ViewModel() {

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _transferProgress = MutableStateFlow<Int>(0)
    val transferProgress: StateFlow<Int> = _transferProgress.asStateFlow()

    private val _transferStatus = MutableStateFlow<FileTransferRepository.TransferStatus?>(null)
    val transferStatus: StateFlow<FileTransferRepository.TransferStatus?> = _transferStatus.asStateFlow()

    init {
        startServer()
        observeDevices()
        observeTransferProgress()
        observeTransferStatus()
    }

    private fun startServer() {
        fileTransferRepository.startServer(8080)
        viewModelScope.launch {
            deviceRepository.registerDevice(8080)
            deviceRepository.startDiscovery()
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            deviceRepository.getDiscoveredDevices().collect { deviceList ->
                _devices.value = deviceList
            }
        }
    }

    private fun observeTransferProgress() {
        viewModelScope.launch {
            fileTransferRepository.getTransferProgress().collect { progress ->
                _transferProgress.value = progress
            }
        }
    }

    private fun observeTransferStatus() {
        viewModelScope.launch {
            fileTransferRepository.observeTransferStatus().collect { status ->
                _transferStatus.value = status
            }
        }
    }

    fun sendFile(device: Device, fileUri: Uri) {
        viewModelScope.launch {
            fileTransferRepository.sendFile(device.host, device.port, fileUri)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            deviceRepository.tearDown()
        }
    }
}