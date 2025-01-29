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
    interface TransferListener {
        fun onProgress(percent: Int)
        fun onSuccess()
        fun onError(message: String)
    }

    private var transferListener: TransferListener? = null

    fun setTransferListener(listener: TransferListener) {
        transferListener = listener
    }

    fun startServer(port: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerSocket(port).use { serverSocket ->
                    while (true) {
                        val socket = serverSocket.accept()
                        receiveFile(socket)
                    }
                }
            } catch (e: IOException) {
                Log.e("FileTransfer", "Server error: ${e.message}")
                transferListener?.onError("Server error: ${e.message}")
            }
        }
    }

    fun sendFile(ip: String, port: Int, fileUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            try {
                // 1. Resolve URI to get file path and original name
                val (filePath, fileName) = getRealPathFromURI(fileUri)
                val file = File(filePath)

                if (!file.exists()) {
                    throw IOException("File not found: $filePath")
                }

                // 2. Establish connection with timeout
                socket = Socket().apply {
                    connect(InetSocketAddress(ip, port), 15000) // 15-second timeout
                }

                // 3. Send file with original filename
                sendFileData(
                    socket = socket!!,
                    filePath = filePath,
                    fileName = fileName
                )

                transferListener?.onSuccess()

            } catch (e: Exception) {
                Log.e("FileTransfer", "Send error", e)
                transferListener?.onError("Send failed: ${e.message ?: "Unknown error"}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun sendFileData(socket: Socket, filePath: String, fileName: String) {
        DataOutputStream(socket.getOutputStream()).use { dos ->
            val file = File(filePath)

            // Send metadata: "filename.extension|filesize"
            val metadata = "$fileName|${file.length()}".toByteArray(Charsets.UTF_8)
            dos.writeInt(metadata.size) // 4-byte length prefix
            dos.write(metadata)

            // Send file data
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    dos.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun receiveFile(socket: Socket) {
        try {
            DataInputStream(socket.getInputStream()).use { dis ->
                // 1. Read metadata length (4 bytes)
                val metadataLength = dis.readInt()

                // 2. Read metadata
                val metadataBytes = ByteArray(metadataLength)
                dis.readFully(metadataBytes)
                val metadata = String(metadataBytes, Charsets.UTF_8)
                val (fileName, fileSize) = metadata.split("|")

                // 3. Prepare output file
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ReceivedFiles"
                ).apply { mkdirs() }

                FileOutputStream(File(downloadsDir, fileName)).use { fos ->
                    // 4. Receive file in chunks
                    val buffer = ByteArray(4096)
                    var remaining = fileSize.toLong()
                    var totalReceived = 0L

                    while (remaining > 0) {
                        val bytesRead = dis.read(buffer, 0, min(buffer.size, remaining.toInt()))
                        if (bytesRead == -1) break

                        fos.write(buffer, 0, bytesRead)
                        totalReceived += bytesRead
                        remaining -= bytesRead
                        updateProgress(totalReceived, fileSize.toLong())
                    }
                }
                transferListener?.onSuccess()
            }
        } catch (e: Exception) {
            transferListener?.onError("Receive error: ${e.message}")
        }
    }

    private fun updateProgress(current: Long, total: Long) {
        val percent = ((current.toDouble() / total.toDouble()) * 100).toInt()
        transferListener?.onProgress(percent)
    }
    @Throws(IOException::class)
    private fun getRealPathFromURI(uri: Uri): Pair<String, String> {
        // 1. Get original filename with extension (e.g., "photo.jpg")
        val fileName = getOriginalFileName(uri) ?: run {
            // Fallback: Generate a unique name without .tmp
            "file_${System.currentTimeMillis()}"
        }

        // 2. Create a temporary file with the original name
        val tempFile = File(context.cacheDir, fileName).apply {
            createNewFile()
            deleteOnExit() // Clean up later
        }

        // 3. Copy file content
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        return Pair(tempFile.absolutePath, fileName)
    }

    private fun getOriginalFileName(uri: Uri): String? {
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
                    } else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }
}