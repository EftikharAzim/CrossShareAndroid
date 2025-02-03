package com.eftikharazim.crossshare.data.repository

import android.content.Context
import android.net.Uri
import com.eftikharazim.crossshare.domain.repository.FileTransferRepository
import com.eftikharazim.crossshare.filetransfer.FileTransfer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow

class FileTransferRepositoryImpl(
    private val context: Context
) : FileTransferRepository {
    private val fileTransfer = FileTransfer(context)
    private val progressFlow = MutableSharedFlow<Int>(replay = 0)
    private val statusFlow = MutableSharedFlow<FileTransferRepository.TransferStatus>(replay = 0)

    init {
        fileTransfer.setTransferListener(object : FileTransfer.TransferListener {
            override fun onProgress(percent: Int) {
                progressFlow.tryEmit(percent)
            }

            override fun onSuccess() {
                statusFlow.tryEmit(FileTransferRepository.TransferStatus.Success)
            }

            override fun onError(message: String) {
                statusFlow.tryEmit(FileTransferRepository.TransferStatus.Error(message))
            }
        })
    }

    override fun startServer(port: Int) {
        fileTransfer.startServer(port)
    }

    override suspend fun sendFile(ip: String, port: Int, fileUri: Uri) {
        fileTransfer.sendFile(ip, port, fileUri)
    }

    override fun getTransferProgress(): Flow<Int> = progressFlow

    override fun observeTransferStatus(): Flow<FileTransferRepository.TransferStatus> = statusFlow
}