package com.eftikharazim.crossshare.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface FileTransferRepository {
    fun startServer(port: Int)
    suspend fun sendFile(ip: String, port: Int, fileUri: Uri)
    fun getTransferProgress(): Flow<Int>
    fun observeTransferStatus(): Flow<TransferStatus>

    sealed class TransferStatus {
        object Success : TransferStatus()
        data class Error(val message: String) : TransferStatus()
    }
}