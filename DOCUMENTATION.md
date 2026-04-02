# Running LLM on Android: A Complete Guide to On-Device AI with Gemma 3 and MediaPipe

*Build a privacy-first AI chat app that runs entirely on your Android device — no internet, no API costs, no data leaving your phone.*

---

In an era where AI assistants send every query to the cloud, there's something refreshing about running a Large Language Model (LLM) entirely on your device. No API keys. No network latency. No privacy concerns. Just pure, local intelligence.

In this article, I'll walk you through building an Android chat application powered by Google's Gemma 3 model using MediaPipe's LLM Inference API. By the end, you'll have a fully functional AI assistant running in your pocket.

## Why On-Device LLM?

Before diving into code, let's understand why you might want to run an LLM locally:

- **Privacy**: Your conversations never leave the device
- **Offline capability**: Works without internet connection
- **Zero API costs**: No per-token billing from cloud providers
- **Low latency**: No network round-trips for small models
- **Control**: You own the entire inference pipeline

The trade-off? Model size limitations. But with Google's Gemma 3 270M variant at just 304MB, you get surprisingly capable AI that fits comfortably on most modern Android devices.

---

## Architecture Overview

Our app follows a clean three-layer architecture:

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │ ChatScreen  │    │  ChatInput  │    │MessageBubble│ │
│  └──────┬──────┘    └─────────────┘    └─────────────┘ │
│         │                                               │
│         ▼                                               │
│  ┌─────────────┐                                        │
│  │ChatViewModel│  (Manages UI state & user actions)     │
│  └──────┬──────┘                                        │
└─────────┼───────────────────────────────────────────────┘
          │
┌─────────┼───────────────────────────────────────────────┐
│         ▼              Data Layer                       │
│  ┌─────────────┐                                        │
│  │ChatRepository│  (Handles LLM inference)              │
│  └──────┬──────┘                                        │
│         │                                               │
│         ▼                                               │
│  ┌──────────────┐                                       │
│  │LlmModelManager│  (Model file management)             │
│  └──────┬───────┘                                       │
└─────────┼───────────────────────────────────────────────┘
          │
┌─────────┼───────────────────────────────────────────────┐
│         ▼           MediaPipe Layer                     │
│  ┌─────────────┐    ┌────────────────────┐              │
│  │ LlmInference│───▶│ LlmInferenceSession│              │
│  └─────────────┘    └────────────────────┘              │
└─────────────────────────────────────────────────────────┘
```

---

## Step 1: Project Setup

### Dependencies

First, add the MediaPipe GenAI dependency to your `libs.versions.toml`:

```toml
[versions]
mediapipeGenai = "0.10.27"

[libraries]
mediapipe-tasks-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipeGenai" }
```

Then in your app's `build.gradle.kts`:

```kotlin
android {
    // Important: Don't compress .task model files
    androidResources {
        noCompress += "task"
    }
}

dependencies {
    implementation(libs.mediapipe.tasks.genai)

    // Jetpack Compose for UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
```

### Download the Model

Get the Gemma 3 model from Kaggle:

1. Visit [Gemma 3 on Kaggle](https://www.kaggle.com/models/google/gemma-3/tfLite)
2. Select the **gemma-3-270m-it-int8** variant
3. Download the `.task` file (~304MB)
4. Place it in `app/src/main/assets/gemma-3-270m-it-int8.task`

---

## Step 2: Model Management

MediaPipe requires a file path to the model, not an asset stream. So we need to copy the model from assets to internal storage on first launch.

### LlmModelManager.kt

```kotlin
class LlmModelManager(private val context: Context) {

    companion object {
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
            val inputStream = context.assets.open(MODEL_FILENAME)
            val outputFile = File(context.filesDir, MODEL_FILENAME)
            val outputStream = FileOutputStream(outputFile)

            // Get file size for progress calculation
            val totalSize = try {
                context.assets.openFd(MODEL_FILENAME).length
            } catch (e: Exception) {
                303950933L // Fallback: known size of model
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var copiedSize = 0L

            inputStream.use { input ->
                outputStream.use { output ->
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
}

sealed class DownloadState {
    data object NotStarted : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}
```

---

## Step 3: The Inference Engine

This is where the magic happens. The `ChatRepository` bridges our app with MediaPipe's LLM Inference API.

### ChatRepository.kt

```kotlin
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

class ChatRepository(
    private val context: Context,
    private val modelManager: LlmModelManager
) {
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null

    suspend fun initializeModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create the inference engine
            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelManager.modelPath)
                .setMaxTokens(1024)  // Maximum response length
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)

            // Step 2: Create a session for conversation
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)           // Consider top 40 tokens
                .setTemperature(0.8f)  // Balance between creativity and coherence
                .setRandomSeed(101)    // For reproducibility
                .build()

            llmSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        val session = llmSession ?: return@withContext "Error: Model not initialized"

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
        llmInference?.close()
    }
}
```

### Understanding the Prompt Format

Gemma models expect a specific conversation format:

```
<start_of_turn>user
Hello, how are you?<end_of_turn>
<start_of_turn>model
```

The model generates text after the `<start_of_turn>model\n` marker. This turn-based format helps the model understand conversation context.

### Inference Parameters Explained

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `maxTokens` | 1024 | Limits response length (~750 words) |
| `topK` | 40 | Only considers the 40 most probable next tokens |
| `temperature` | 0.8 | Controls randomness (0=deterministic, 1=creative) |
| `randomSeed` | 101 | Ensures reproducible outputs for testing |

---

## Step 4: State Management with ViewModel

The `ChatViewModel` manages UI state and coordinates between the UI and repository layers.

### ChatViewModel.kt

```kotlin
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
    val modelState: ModelState = ModelState.NotDownloaded
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val modelManager = LlmModelManager(application)
    private val repository = ChatRepository(application, modelManager)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        checkModelStatus()
    }

    private fun checkModelStatus() {
        if (modelManager.isModelDownloaded()) {
            loadModel()
        } else {
            _uiState.value = _uiState.value.copy(modelState = ModelState.NotDownloaded)
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            modelManager.copyModelFromAssets().collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        _uiState.value = _uiState.value.copy(
                            modelState = ModelState.Downloading(state.progress)
                        )
                    }
                    is DownloadState.Completed -> {
                        loadModel()
                    }
                    is DownloadState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            modelState = ModelState.Error(state.message)
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(modelState = ModelState.Loading)

            repository.initializeModel().fold(
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
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(
            content = content,
            isFromUser = true
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true
        )

        viewModelScope.launch {
            val response = repository.sendMessage(content)

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
        repository.close()
    }
}
```

---

## Step 5: Building the UI with Jetpack Compose

### ChatScreen.kt

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(
                uiState.messages.size - 1 + if (uiState.isLoading) 1 else 0
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("LLM Chat") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main content based on model state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uiState.modelState) {
                    is ModelState.NotDownloaded -> {
                        ModelDownloadPrompt(onDownload = viewModel::downloadModel)
                    }
                    is ModelState.Downloading -> {
                        DownloadProgress(progress = state.progress)
                    }
                    is ModelState.Loading -> {
                        CircularProgressIndicator()
                        Text("Loading model...")
                    }
                    is ModelState.Error -> {
                        ErrorState(
                            message = state.message,
                            onRetry = viewModel::downloadModel
                        )
                    }
                    is ModelState.Ready -> {
                        MessageList(
                            messages = uiState.messages,
                            isLoading = uiState.isLoading,
                            listState = listState
                        )
                    }
                }
            }

            // Input field
            ChatInput(
                onSend = viewModel::sendMessage,
                enabled = uiState.modelState == ModelState.Ready && !uiState.isLoading
            )
        }
    }
}
```

---

## Model Variants

Choose the right model for your use case:

| Variant | Size | RAM Required | Best For |
|---------|------|--------------|----------|
| gemma-3-270m-it-int8 | ~304MB | 4GB+ | Mobile devices, fast responses |
| gemma-3-1b-it-int8 | ~1GB | 6GB+ | Better quality, flagship phones |
| gemma-3-4b-it-int8 | ~4GB | 8GB+ | Best quality, high-end devices |

The 270M variant offers the best balance of size and capability for most mobile applications.

---

## Performance Tips

1. **Use INT8 Quantization**: The `-int8` variants are optimized for mobile inference
2. **Limit Context Length**: Keep conversations short to maintain speed
3. **Preload on App Start**: Initialize the model during splash screen
4. **GPU Acceleration**: MediaPipe automatically uses GPU delegates when available
5. **Background Threading**: Always run inference on `Dispatchers.IO`

---

## Conclusion

Running LLMs on-device opens up exciting possibilities for privacy-focused AI applications. With Google's MediaPipe and Gemma 3, you can build sophisticated AI assistants that work entirely offline.

---

**Key Takeaways:**
- On-device LLMs provide privacy, offline capability, and zero API costs
- MediaPipe's LLM Inference API makes integration straightforward
- Gemma 3 270M offers good capability at just 304MB
- Clean architecture (UI → ViewModel → Repository → MediaPipe) keeps code maintainable

---

## Preview

<p align="center">
  <img src="https://imgur.com/NLXUWYA.png" width="300" alt="App Preview">
</p>

---

## APK Size

One important consideration for on-device LLM apps is the final APK size. Here's the breakdown:

<p align="center">
  <img src="https://imgur.com/rVENDzL.png" width="600" alt="APK Size Breakdown">
</p>

The model file (~304MB) is the largest component. Consider using Android App Bundles (AAB) or dynamic feature modules to optimize delivery.

---

## References

- [MediaPipe LLM Inference Documentation](https://developers.google.com/mediapipe/solutions/genai/llm_inference)
- [Gemma 3 Models on Kaggle](https://www.kaggle.com/models/google/gemma-3/tfLite)
- [Source Code Repository](https://github.com/user/llmondevice)