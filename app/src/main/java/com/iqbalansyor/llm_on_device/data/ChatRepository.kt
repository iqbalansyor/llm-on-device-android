package com.iqbalansyor.llm_on_device.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val context: Context) {

    private val modelManager = LlmModelManager(context)
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null

    val isModelReady: Boolean
        get() = llmSession != null

    val isModelDownloaded: Boolean
        get() = modelManager.isModelDownloaded()

    fun getModelManager(): LlmModelManager = modelManager

    suspend fun initializeModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!modelManager.isModelDownloaded()) {
                return@withContext Result.failure(Exception("Model not downloaded"))
            }

            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelManager.modelPath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.8f)
                .setRandomSeed(101)
                .build()

            llmSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        val session = llmSession
            ?: return@withContext "Error: Model not initialized. Please download and load the model first."

        try {
            val prompt = formatPrompt(userMessage)
            session.addQueryChunk(prompt)
            session.generateResponse()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun formatPrompt(userMessage: String): String {
        return "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
    }

    fun close() {
        llmSession?.close()
        llmSession = null
        llmInference?.close()
        llmInference = null
    }
}
