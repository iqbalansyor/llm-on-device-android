# Integrating Google Gemini API: Adding Cloud LLM to an On-Device AI App

*Extend your on-device LLM app with Google's Gemini API — give users the choice between local privacy and cloud-powered intelligence.*

---

Running an LLM entirely on-device is great for privacy and offline use, but sometimes you need the full power of a cloud model. By integrating Google's Gemini API alongside the local Gemma 3 model, users can switch between providers depending on their needs — fast local inference for privacy, or Gemini for more capable responses.

In this guide, I'll walk you through adding Gemini 2.0 Flash to the existing on-device LLM chat app, including how tokenization differs between local and cloud inference.

## Why Add Gemini?

| | Local (Gemma 3) | Cloud (Gemini) |
|---|---|---|
| **Privacy** | Data stays on device | Data sent to Google servers |
| **Internet** | Not required | Required |
| **Cost** | Free | Free tier available, pay per token at scale |
| **Model Size** | 270M parameters | Billions of parameters |
| **Response Quality** | Good for simple tasks | Excellent for complex reasoning |
| **Context Window** | 2048 tokens | 1,048,576 tokens |
| **Speed** | Depends on device hardware | Consistent, fast |

Having both options lets users choose the right trade-off for each conversation.

---

## Architecture

Adding Gemini introduces a dual-provider architecture. The ViewModel routes messages to either the local or cloud repository based on the user's selection.

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌──────────────┐  ┌───────────────────┐  ┌─────────────┐  │
│  │  ChatScreen  │  │ProviderDropdown   │  │MessageBubble│  │
│  └──────┬───────┘  └───────────────────┘  └─────────────┘  │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────┐                                           │
│  │ChatViewModel │  (Routes to selected provider)            │
│  └──────┬───────┘                                           │
└─────────┼───────────────────────────────────────────────────┘
          │
          ├──────────────────────────────┐
          │                              │
┌─────────▼──────────────┐   ┌──────────▼──────────────┐
│   Local Provider       │   │   Cloud Provider         │
│  ┌────────────────┐    │   │  ┌──────────────────┐   │
│  │ ChatRepository │    │   │  │GeminiRepository  │   │
│  └───────┬────────┘    │   │  └───────┬──────────┘   │
│          │             │   │          │              │
│          ▼             │   │          ▼              │
│  ┌──────────────┐      │   │  ┌──────────────────┐  │
│  │  MediaPipe   │      │   │  │ Google AI SDK    │  │
│  │ LlmInference │      │   │  │ GenerativeModel  │  │
│  └──────────────┘      │   │  └──────────────────┘  │
│                        │   │                         │
│  Offline, On-Device    │   │  Online, Cloud-Based    │
└────────────────────────┘   └─────────────────────────┘
```

---

## Step 1: Get a Gemini API Key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Sign in with your Google account
3. Click **Create API Key**
4. Copy the key

Add it to your `local.properties` (this file is gitignored, so your key stays private):

```properties
GEMINI_API_KEY=your_api_key_here
```

---

## Step 2: Add the Dependency

### gradle/libs.versions.toml

```toml
[versions]
generativeai = "0.9.0"

[libraries]
google-generativeai = { group = "com.google.ai.client.generativeai", name = "generativeai", version.ref = "generativeai" }
```

### app/build.gradle.kts

```kotlin
dependencies {
    implementation(libs.google.generativeai)
}
```

---

## Step 3: Expose the API Key via BuildConfig

The API key needs to be available at runtime without hardcoding it in source code. We read it from `local.properties` and inject it as a `BuildConfig` field.

### app/build.gradle.kts

```kotlin
import java.util.Properties

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    defaultConfig {
        // Inject API key into BuildConfig
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    buildFeatures {
        buildConfig = true  // Enable BuildConfig generation
    }
}
```

After syncing Gradle, you can access `BuildConfig.GEMINI_API_KEY` anywhere in your code.

**Important**: Never commit your API key. Ensure `local.properties` is in `.gitignore`.

---

## Step 4: Create the Gemini Repository

### data/GeminiRepository.kt

```kotlin
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
```

### Key Concepts

#### GenerativeModel
The entry point to the Gemini API. Takes a model name and API key:
```kotlin
val generativeModel = GenerativeModel(
    modelName = "gemini-2.0-flash",
    apiKey = BuildConfig.GEMINI_API_KEY
)
```

#### Stateful Chat
`startChat()` creates a chat session that automatically maintains conversation history. Each `sendMessage()` call appends the user message and model response to the history, so the model remembers context from earlier in the conversation.

```kotlin
val chat = generativeModel.startChat(
    history = listOf(
        content(role = "user") { text("Hello, I'd like to have a conversation with you.") },
        content(role = "model") { text("Hello! I'm happy to chat with you. How can I help you today?") }
    )
)
```

The initial history seeds the conversation with a greeting, so the model starts in a conversational tone.

#### content() DSL
The Google AI SDK uses a Kotlin DSL for building content:
```kotlin
content(role = "user") { text("Hello!") }
content(role = "model") { text("Hi there!") }
```

Roles:
- `"user"` — the human's message
- `"model"` — the AI's response

---

## Step 5: Create the Provider Model

### model/LlmProvider.kt

```kotlin
package com.iqbalansyor.llm_on_device.model

enum class LlmProvider(val displayName: String) {
    LOCAL("Local (Gemma 3)"),
    GOOGLE_AI("Google AI (Gemini)")
}
```

This enum lets the UI and ViewModel reference providers in a type-safe way.

---

## Step 6: Update the ViewModel

The ViewModel now holds both repositories and routes messages based on the selected provider.

### ui/ChatViewModel.kt

```kotlin
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val localRepository = ChatRepository(application)
    private val geminiRepository = GeminiRepository()

    // UI state now includes selectedProvider
    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val isLoading: Boolean = false,
        val modelState: ModelState = ModelState.NotDownloaded,
        val selectedProvider: LlmProvider = LlmProvider.LOCAL
    )

    fun setProvider(provider: LlmProvider) {
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            messages = emptyList()  // Clear chat when switching
        )
        if (provider == LlmProvider.LOCAL) {
            checkModelStatus()  // Re-check local model state
        }
    }

    fun sendMessage(content: String) {
        // ...
        viewModelScope.launch {
            val response = when (_uiState.value.selectedProvider) {
                LlmProvider.LOCAL -> localRepository.sendMessage(content)
                LlmProvider.GOOGLE_AI -> geminiRepository.sendMessage(content)
            }
            // ...
        }
    }
}
```

Key differences from the local-only ViewModel:
1. **Two repositories** — `localRepository` for on-device, `geminiRepository` for cloud
2. **Provider switching** — `setProvider()` clears the chat and updates the UI state
3. **Routing** — `sendMessage()` delegates to the correct repository via `when`

---

## Step 7: Update the UI

### Provider Dropdown

A dropdown in the top bar lets users switch providers:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selectedProvider: LlmProvider,
    onProviderSelected: (LlmProvider) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedProvider.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LlmProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        onProviderSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}
```

### Conditional UI Based on Provider

The chat screen renders differently depending on the provider:

```kotlin
when (uiState.selectedProvider) {
    LlmProvider.GOOGLE_AI -> {
        // Google AI is always ready — no model download needed
        // Show chat interface immediately
    }
    LlmProvider.LOCAL -> {
        // Show model state machine: NotDownloaded → Downloading → Loading → Ready
        when (val modelState = uiState.modelState) {
            is ModelState.NotDownloaded -> ModelDownloadPrompt(...)
            is ModelState.Downloading -> DownloadingIndicator(...)
            is ModelState.Loading -> LoadingModelIndicator(...)
            is ModelState.Error -> ErrorState(...)
            is ModelState.Ready -> { /* Show chat */ }
        }
    }
}
```

The input field is enabled based on the provider:
```kotlin
val isInputEnabled = when (uiState.selectedProvider) {
    LlmProvider.GOOGLE_AI -> !uiState.isLoading  // Always ready
    LlmProvider.LOCAL -> uiState.modelState == ModelState.Ready && !uiState.isLoading
}
```

---

## Tokenization: Local vs Cloud

Tokenization is fundamental to how LLMs process text. Both local (Gemma 3) and cloud (Gemini) models tokenize input, but the process differs in visibility and scale.

### How Tokenization Works (Recap)

```
"Hello, how are you?"
       ↓ Tokenization
["Hello", ",", " how", " are", " you", "?"]
       ↓ Token IDs
[15496, 11, 703, 527, 499, 30]
       ↓ Model Processing
[Neural network processes token embeddings]
       ↓ Token Generation
[Outputs tokens one by one]
       ↓ Detokenization
"I'm doing well, thank you!"
```

### Local Tokenization (Gemma 3 via MediaPipe)

With on-device inference, tokenization happens entirely inside MediaPipe. You never see raw tokens — the SDK handles everything:

```kotlin
// You provide text
session.addQueryChunk("<start_of_turn>user\nHello<end_of_turn>\n<start_of_turn>model\n")

// MediaPipe internally:
// 1. Tokenizes with SentencePiece (~256K vocabulary)
// 2. Runs the model on token embeddings
// 3. Generates output tokens using TopK + Temperature
// 4. Detokenizes back to text
val response = session.generateResponse()
```

```
┌─────────────────────────────────────────────────────┐
│                MediaPipe (All Internal)              │
│                                                     │
│  Text → [Tokenizer] → [Model] → [Detokenizer] → Text │
│         SentencePiece   TFLite                      │
│         ~256K vocab     On-device                   │
└─────────────────────────────────────────────────────┘
```

**Token limits are your responsibility:**
```kotlin
.setMaxTokens(1024)      // You set max response tokens
.setTopK(40)             // You control sampling
.setTemperature(0.8f)    // You control creativity
```

### Cloud Tokenization (Gemini API)

With the Gemini API, tokenization happens on Google's servers. The SDK sends raw text over the network, and Google's infrastructure handles tokenization, inference, and detokenization:

```kotlin
// You provide text
val response = chat.sendMessage("Hello")

// Google's servers internally:
// 1. Receive text over HTTPS
// 2. Tokenize with Gemini's tokenizer
// 3. Run the model (distributed across TPU clusters)
// 4. Generate output tokens
// 5. Detokenize and return text
response.text  // "Hi there! How can I help you?"
```

```
┌──────────────┐         ┌──────────────────────────────────┐
│  Your App    │  HTTPS  │      Google Cloud Servers         │
│              │────────▶│                                    │
│ sendMessage()│         │ Text → [Tokenizer] → [Model] →   │
│              │◀────────│        [Detokenizer] → Text       │
│ response.text│         │                                    │
└──────────────┘         │ Gemini tokenizer    TPU clusters  │
                         │ Massive vocab       Billions of   │
                         │                     parameters    │
                         └──────────────────────────────────┘
```

**Token limits are managed by the API:**
- Input and output token counts are tracked server-side
- You are billed per token (input and output)
- The model automatically handles sampling parameters

### Token Limits Comparison

| | Gemma 3 270M (Local) | Gemini 2.0 Flash (Cloud) |
|---|---|---|
| **Tokenizer** | SentencePiece | Gemini tokenizer |
| **Vocabulary** | ~256K tokens | Large vocabulary |
| **Context Window** | 2,048 tokens | 1,048,576 tokens |
| **Max Output** | 1,024 tokens (configurable) | 8,192 tokens (default) |
| **Sampling Control** | TopK, Temperature, Seed | Temperature, TopP, TopK, and more |
| **Token Counting** | Not exposed by MediaPipe | Available via `countTokens()` |

### Counting Tokens with Gemini

The Gemini SDK provides a `countTokens()` method to check how many tokens a message will consume before sending it:

```kotlin
val generativeModel = GenerativeModel(
    modelName = "gemini-2.0-flash",
    apiKey = BuildConfig.GEMINI_API_KEY
)

// Count tokens before sending
val tokenCount = generativeModel.countTokens("What is the capital of France?")
println("Total tokens: ${tokenCount.totalTokens}")
// Output: Total tokens: 8
```

This is useful for:
- **Cost estimation** — knowing how many tokens you'll be billed for
- **Context management** — staying within the model's context window
- **Rate limiting** — tracking usage against API quotas

With the local model (MediaPipe), token counting is not exposed — MediaPipe handles it internally and you only control the max output token count via `setMaxTokens()`.

### Token Approximation Rules

For both local and cloud models, the same rough approximation applies for English text:

| Text | Approximate Tokens |
|------|-------------------|
| 1 word | ~1.3 tokens |
| 1 sentence | ~15-20 tokens |
| 1 paragraph (100 words) | ~130 tokens |
| 1 page (~500 words) | ~650 tokens |

### Prompt Format and Token Overhead

**Local (Gemma 3)** — uses explicit turn markers that consume tokens:
```
<start_of_turn>user       ← ~4 tokens overhead
What is AI?               ← your message tokens
<end_of_turn>             ← ~3 tokens overhead
<start_of_turn>model      ← ~4 tokens overhead
```

Each message has ~11 tokens of formatting overhead. With a 2,048-token context window, this overhead matters.

**Cloud (Gemini)** — the SDK handles formatting internally:
```kotlin
chat.sendMessage("What is AI?")  // No manual formatting needed
```

The API manages conversation structure. With a 1M+ token context window, formatting overhead is negligible.

---

## Gemini Model Variants

| Model | Speed | Quality | Context | Best For |
|-------|-------|---------|---------|----------|
| gemini-2.0-flash | Fast | High | 1M tokens | General use, chat apps |
| gemini-2.0-flash-lite | Fastest | Good | 1M tokens | Cost-sensitive, simple tasks |
| gemini-2.5-pro | Moderate | Highest | 1M tokens | Complex reasoning, analysis |
| gemini-2.5-flash | Fast | Very High | 1M tokens | Balanced speed and quality |

This app uses **gemini-2.0-flash** for the best balance of speed and quality in a chat interface.

To switch models, change the `modelName`:
```kotlin
val generativeModel = GenerativeModel(
    modelName = "gemini-2.5-flash",  // or any other variant
    apiKey = BuildConfig.GEMINI_API_KEY
)
```

---

## Error Handling

The Gemini repository wraps API calls in try-catch to handle common failures:

```kotlin
suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
    try {
        val response = chat.sendMessage(userMessage)
        response.text ?: "No response received"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
```

Common errors you may encounter:

| Error | Cause | Fix |
|-------|-------|-----|
| `API key not valid` | Invalid or missing API key | Check `local.properties` |
| `RESOURCE_EXHAUSTED` | Rate limit exceeded | Wait and retry, or upgrade quota |
| `UnknownHostException` | No internet connection | Check network connectivity |
| `SAFETY` block | Content filtered by safety settings | Rephrase the message |
| `No response received` | Empty response from API | Retry the request |

---

## File Structure After Integration

```
app/src/main/java/com/iqbalansyor/llm_on_device/
├── MainActivity.kt
├── data/
│   ├── ChatRepository.kt         # Local LLM inference (MediaPipe)
│   ├── GeminiRepository.kt       # Cloud LLM inference (Gemini API)  ← NEW
│   └── LlmModelManager.kt        # Model file management
├── model/
│   ├── ChatMessage.kt             # Message data class
│   └── LlmProvider.kt            # Provider enum (LOCAL, GOOGLE_AI)  ← NEW
└── ui/
    ├── ChatScreen.kt              # Updated with provider dropdown
    ├── ChatViewModel.kt           # Updated with dual-provider routing
    └── components/
        ├── ChatInput.kt
        ├── MessageBubble.kt
        ├── MessageList.kt
        └── TypingIndicator.kt
```

---

## Dependencies Summary

```toml
# gradle/libs.versions.toml
[versions]
mediapipeGenai = "0.10.27"    # On-device inference
generativeai = "0.9.0"        # Gemini API client

[libraries]
mediapipe-tasks-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipeGenai" }
google-generativeai = { group = "com.google.ai.client.generativeai", name = "generativeai", version.ref = "generativeai" }
```

---

## Key Takeaways

1. **Dual-provider architecture** — users choose between local privacy and cloud intelligence
2. **Gemini SDK is minimal** — `GenerativeModel` + `startChat()` + `sendMessage()` is all you need
3. **Stateful chat** — the SDK automatically maintains conversation history
4. **API key security** — store in `local.properties`, inject via `BuildConfig`, never commit
5. **Tokenization differs** — local uses SentencePiece internally, cloud handles it server-side
6. **Token counting** — `countTokens()` is available for Gemini but not for MediaPipe
7. **No model download** — Gemini works immediately, unlike local models that need setup
8. **Context window** — Gemini's 1M tokens dwarfs Gemma 3's 2K, enabling longer conversations

---

## References

- [Google AI SDK for Android](https://ai.google.dev/gemini-api/docs/quickstart?lang=android)
- [Gemini API Documentation](https://ai.google.dev/gemini-api/docs)
- [Google AI Studio (API Keys)](https://aistudio.google.com/apikey)
- [Gemini Model Variants](https://ai.google.dev/gemini-api/docs/models)
- [Token Counting](https://ai.google.dev/gemini-api/docs/tokens)
- [Source Code Repository](https://github.com/user/llmondevice)
