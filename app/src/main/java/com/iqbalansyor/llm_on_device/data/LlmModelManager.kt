package com.iqbalansyor.llm_on_device.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

sealed class DownloadState {
    data object NotStarted : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class LlmModelManager(private val context: Context) {

    companion object {
        private const val ASSET_MODEL_FILENAME = "gemma-3-270m-it-int8.task"
        private const val MODEL_FILENAME = "gemma-3-270m-it-int8.task"
    }

    val modelPath: String
        get() = File(context.filesDir, MODEL_FILENAME).absolutePath

    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun copyModelFromAssets(): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))

        try {
            val outputFile = File(context.filesDir, MODEL_FILENAME)

            if (outputFile.exists()) {
                emit(DownloadState.Completed)
                return@flow
            }

            context.assets.open(ASSET_MODEL_FILENAME).use { input ->
                val totalSize = context.assets.openFd(ASSET_MODEL_FILENAME).length
                var copiedSize = 0L

                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copiedSize += bytesRead

                        val progress = ((copiedSize * 100) / totalSize).toInt()
                        emit(DownloadState.Downloading(progress))
                    }
                }
            }

            emit(DownloadState.Completed)
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteModel() {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        if (modelFile.exists()) {
            modelFile.delete()
        }
    }
}
