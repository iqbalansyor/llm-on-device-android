package com.iqbalansyor.llm_on_device.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.iqbalansyor.llm_on_device.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val chat = generativeModel.startChat(
        history = listOf(
            content(role = "user") { text("Hello, I'd like to have a conversation with you.") },
            content(role = "model") { text("Hello! I'm happy to chat with you. How can I help you today?") }
        )
    )

    suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            val response = chat.sendMessage(userMessage)
            response.text ?: "No response received"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
