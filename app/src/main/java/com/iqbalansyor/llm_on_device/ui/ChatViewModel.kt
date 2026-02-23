package com.iqbalansyor.llm_on_device.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqbalansyor.llm_on_device.data.ChatRepository
import com.iqbalansyor.llm_on_device.data.DownloadState
import com.iqbalansyor.llm_on_device.data.GeminiRepository
import com.iqbalansyor.llm_on_device.model.ChatMessage
import com.iqbalansyor.llm_on_device.model.LlmProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ModelState {
    data object NotDownloaded : ModelState()
    data class Downloading(val progress: Int) : ModelState()
    data object Loading : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val modelState: ModelState = ModelState.NotDownloaded,
    val selectedProvider: LlmProvider = LlmProvider.LOCAL
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val localRepository = ChatRepository(application)
    private val geminiRepository = GeminiRepository()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        checkModelStatus()
    }

    fun setProvider(provider: LlmProvider) {
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            messages = emptyList()
        )
        if (provider == LlmProvider.LOCAL) {
            checkModelStatus()
        }
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            if (localRepository.isModelDownloaded) {
                _uiState.value = _uiState.value.copy(modelState = ModelState.Loading)
                loadModel()
            } else {
                _uiState.value = _uiState.value.copy(modelState = ModelState.NotDownloaded)
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            localRepository.getModelManager().copyModelFromAssets().collect { state ->
                when (state) {
                    is DownloadState.NotStarted -> {
                        _uiState.value = _uiState.value.copy(modelState = ModelState.NotDownloaded)
                    }
                    is DownloadState.Downloading -> {
                        _uiState.value = _uiState.value.copy(
                            modelState = ModelState.Downloading(state.progress)
                        )
                    }
                    is DownloadState.Completed -> {
                        _uiState.value = _uiState.value.copy(modelState = ModelState.Loading)
                        loadModel()
                    }
                    is DownloadState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            modelState = ModelState.Error(state.message)
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadModel() {
        val result = localRepository.initializeModel()
        result.fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(modelState = ModelState.Ready)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    modelState = ModelState.Error(error.message ?: "Failed to load model")
                )
            }
        )
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val currentProvider = _uiState.value.selectedProvider
        if (currentProvider == LlmProvider.LOCAL && _uiState.value.modelState != ModelState.Ready) return

        val userMessage = ChatMessage(
            content = content,
            isFromUser = true
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true
        )

        viewModelScope.launch {
            val response = when (currentProvider) {
                LlmProvider.LOCAL -> localRepository.sendMessage(content)
                LlmProvider.GOOGLE_AI -> geminiRepository.sendMessage(content)
            }
            val assistantMessage = ChatMessage(
                content = response,
                isFromUser = false
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + assistantMessage,
                isLoading = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        localRepository.close()
    }
}
