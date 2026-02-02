package com.iqbalansyor.llm_on_device.data

import kotlinx.coroutines.delay

class ChatRepository {

    private val dummyResponses = listOf(
        "That's an interesting question! Let me think about it...",
        "I understand what you're saying. Here's my perspective on that.",
        "Great point! I'd like to add that there are multiple ways to look at this.",
        "Thanks for sharing that with me. It's always good to explore new ideas.",
        "Hmm, that's a thought-provoking topic. Let me elaborate.",
        "I appreciate your curiosity! Here's what I know about that.",
        "That's a complex subject, but I'll do my best to explain.",
        "Interesting! I hadn't considered it from that angle before.",
        "You raise a valid point. Let me share some thoughts on this.",
        "I'm glad you asked! This is something I find fascinating."
    )

    suspend fun sendMessage(userMessage: String): String {
        // Simulate network/processing delay
        delay((1000..2000).random().toLong())

        // Return a random dummy response
        // Later this will be replaced with actual LLM call
        return dummyResponses.random()
    }
}
