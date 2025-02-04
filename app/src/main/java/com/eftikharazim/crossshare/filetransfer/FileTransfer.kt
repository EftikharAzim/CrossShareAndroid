package com.eftikharazim.crossshare.filetransfer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.log
import kotlin.math.min

class FileTransfer(private val context: Context) {
    companion object {
        private const val TAG = "FileTransfer"
        private const val BUFFER_SIZE = 4096
    }

    interface TransferListener {
        fun onProgress(percent: Int)
        fun onSuccess()
        fun onError(message: String)
    }

    private var transferListener: TransferListener? = null

    fun setTransferListener(listener: TransferListener) {
        transferListener = listener
        Log.d(TAG, "Transfer listener set")
    }

    fun startServer(port: Int) {
        Log.i(TAG, "Starting server on port: $port")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerSocket(port).use { serverSocket ->
                    Log.d(TAG, "Server socket created successfully")
                    while (true) {
                        Log.d(TAG, "Waiting for incoming connections...")
                        val socket = serverSocket.accept()
                        Log.i(TAG, "Client connected from: ${socket.inetAddress.hostAddress}")
                        receiveFile(socket)
                    }
                }
            } catch (e: IOException) {
                val errorMsg = "Server error: ${e.message}"
                Log.e(TAG, errorMsg, e)
                transferListener?.onError(errorMsg)
            }
        }
    }

    fun sendFile(ip: String, port: Int, fileUri: Uri) {
        Log.i(TAG, "Initiating file transfer to $ip:$port")
        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            try {
                Log.d(TAG, "Resolving file URI: $fileUri")
                val (filePath, fileName) = getRealPathFromURI(fileUri)
                val file = File(filePath)

                if (!file.exists()) {
                    val errorMsg = "File not found: $filePath"
                    Log.e(TAG, errorMsg)
                    throw IOException(errorMsg)
                }
                Log.d(TAG, "File details - Name: $fileName, Size: ${file.length()} bytes")

                Log.d(TAG, "Establishing connection to $ip:$port")
                socket = Socket().apply {
                    connect(InetSocketAddress(ip, port), 15000)
                }
                Log.i(TAG, "Connected to receiver")

                sendFileData(socket!!, filePath, fileName)
                Log.i(TAG, "File sent successfully: $fileName")
                transferListener?.onSuccess()

            } catch (e: Exception) {
                val errorMsg = "Send failed: ${e.message ?: "Unknown error"}"
                Log.e(TAG, errorMsg, e)
                transferListener?.onError(errorMsg)
            } finally {
                socket?.close()
                Log.d(TAG, "Connection closed")
            }
        }
    }

    private fun sendFileData(socket: Socket, filePath: String, fileName: String) {
        Log.d(TAG, "Starting to send file data for: $fileName")
        DataOutputStream(socket.getOutputStream()).use { dos ->
            val file = File(filePath)

            val metadata = "$fileName|${file.length()}".toByteArray(Charsets.UTF_8)
            Log.v(TAG, "Sending metadata: ${metadata.size} bytes")
            dos.writeInt(metadata.size)
            dos.write(metadata)

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalSent = 0L
                val fileSize = file.length()

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    dos.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    val progress = ((totalSent.toDouble() / fileSize) * 100).toInt()
                    Log.v(TAG, "Send progress: $progress% ($totalSent/$fileSize bytes)")
                    updateProgress(totalSent, fileSize)
                }
            }
            Log.d(TAG, "File data sent successfully")
        }
    }

    private fun receiveFile(socket: Socket) {
        Log.d(TAG, "Starting to receive file from: ${socket.inetAddress.hostAddress}")
        try {
            DataInputStream(socket.getInputStream()).use { dis ->
                val metadataLength = dis.readInt()
                Log.d(TAG, "Received metadata length: $metadataLength bytes")

                val metadataBytes = ByteArray(metadataLength)
                dis.readFully(metadataBytes)
                val metadata = String(metadataBytes, Charsets.UTF_8)
                val (fileName, fileSize) = metadata.split("|") 
                Log.i(TAG, "Receiving file: $fileName, size: $fileSize bytes")

                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ReceivedFiles"
                ).apply { mkdirs() }

                val outputFile = File(downloadsDir, fileName)
                Log.d(TAG, "Saving file to: ${outputFile.absolutePath}")

                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = fileSize.toLong()
                    var totalReceived = 0L

                    while (remaining > 0) {
                        val bytesRead = dis.read(buffer, 0, min(buffer.size, remaining.toInt()))
                        if (bytesRead == -1) {
                            val errorMsg = "Unexpected end of stream"
                            Log.e(TAG, errorMsg)
                            throw IOException(errorMsg)
                        }

                        fos.write(buffer, 0, bytesRead)
                        totalReceived += bytesRead
                        remaining -= bytesRead
                        val progress = ((totalReceived.toDouble() / fileSize.toLong()) * 100).toInt()
                        Log.v(TAG, "Receive progress: $progress% ($totalReceived/$fileSize bytes)")
                        updateProgress(totalReceived, fileSize.toLong())
                    }
                }
                Log.i(TAG, "File received successfully: $fileName")
                transferListener?.onSuccess()
            }
        } catch (e: Exception) {
            val errorMsg = "Receive error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            transferListener?.onError(errorMsg)
        }
    }

    private fun updateProgress(current: Long, total: Long) {
        val percent = ((current.toDouble() / total.toDouble()) * 100).toInt()
        transferListener?.onProgress(percent)
    }

    @Throws(IOException::class)
    private fun getRealPathFromURI(uri: Uri): Pair<String, String> {
        Log.d(TAG, "Getting real path for URI: $uri")
        val fileName = getOriginalFileName(uri) ?: run {
            val generatedName = "file_${System.currentTimeMillis()}"
            Log.w(TAG, "Original filename not found, using generated name: $generatedName")
            generatedName
        }

        val tempFile = File(context.cacheDir, fileName).apply {
            createNewFile()
            deleteOnExit()
        }
        Log.d(TAG, "Created temporary file: ${tempFile.absolutePath}")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                val copied = input.copyTo(output)
                Log.d(TAG, "Copied $copied bytes to temporary file")
            }
        }

        return Pair(tempFile.absolutePath, fileName)
    }

    private fun getOriginalFileName(uri: Uri): String? {
        Log.v(TAG, "Attempting to get original filename for URI: $uri")
        return when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                            .also { Log.d(TAG, "Found original filename: $it") }
                    } else {
                        Log.w(TAG, "No display name found in content resolver")
                        null
                    }
                }
            }
            "file" -> uri.lastPathSegment?.also { Log.d(TAG, "Using last path segment as filename: $it") }
            else -> {
                Log.w(TAG, "Unsupported URI scheme: ${uri.scheme}")
                null
            }
        }
    }
}