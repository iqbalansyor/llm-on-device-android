# Architecture

This document explains how the LLM on Device Android app works.

## Overview

The app runs a Large Language Model (LLM) entirely on the device using Google's MediaPipe LLM Inference API. No internet connection is required for chat functionality.

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

## Components

### UI Layer

#### ChatScreen (`ui/ChatScreen.kt`)
The main screen that displays:
- Model download prompt (if model not installed)
- Download progress indicator
- Chat messages list
- Input field for user messages

Handles different model states:
- `NotDownloaded` - Shows download button
- `Downloading` - Shows progress bar
- `Loading` - Shows loading spinner
- `Ready` - Shows chat interface
- `Error` - Shows error with retry option

#### ChatViewModel (`ui/ChatViewModel.kt`)
Manages the UI state using Kotlin StateFlow:
- Tracks model state (downloading, loading, ready, error)
- Handles user message sending
- Coordinates between UI and repository

### Data Layer

#### ChatRepository (`data/ChatRepository.kt`)
The bridge between the app and MediaPipe:
- Initializes the LLM inference engine
- Creates and manages inference sessions
- Formats prompts for Gemma model
- Sends messages and receives responses

Key methods:
```kotlin
suspend fun initializeModel(): Result<Unit>  // Load model into memory
suspend fun sendMessage(userMessage: String): String  // Generate response
```

#### LlmModelManager (`data/LlmModelManager.kt`)
Handles model file operations:
- Copies model from assets to internal storage
- Tracks copy progress
- Checks if model is already installed

The model must be copied to internal storage because MediaPipe requires a file path, not an asset stream.

## How LLM Inference Works

### 1. Model Loading
```kotlin
val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelPath)      // Path to .task file
    .setMaxTokens(1024)           // Max response length
    .build()

llmInference = LlmInference.createFromOptions(context, inferenceOptions)
```

### 2. Session Creation
```kotlin
val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setTopK(40)           // Consider top 40 tokens
    .setTemperature(0.8f)  // Creativity level (0=deterministic, 1=creative)
    .setRandomSeed(101)    // For reproducibility
    .build()

llmSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
```

### 3. Generating Response
```kotlin
// Format prompt for Gemma model
val prompt = "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"

// Add to session and generate
session.addQueryChunk(prompt)
val response = session.generateResponse()
```

## Gemma 3 Prompt Format

The Gemma model expects a specific prompt format:

```
<start_of_turn>user
Hello, how are you?<end_of_turn>
<start_of_turn>model
```

The model will then generate text continuing from `<start_of_turn>model\n`.

## Model Variants

Available Gemma 3 variants on Kaggle:

| Variant | Size | Use Case |
|---------|------|----------|
| gemma-3-270m-it-int8 | ~304MB | Mobile devices, fastest |
| gemma-3-1b-it-int8 | ~1GB | Better quality, needs more RAM |
| gemma-3-4b-it-int8 | ~4GB | Best quality, high-end devices |

This app uses the 270M variant for broad device compatibility.

## MediaPipe LLM Inference

The app uses [MediaPipe Tasks](https://developers.google.com/mediapipe/solutions/genai/llm_inference) for on-device inference:

- **LlmInference**: Main engine that loads and runs the model
- **LlmInferenceSession**: Manages conversation context and generates responses

Benefits:
- Runs entirely on-device (privacy)
- No API costs
- Works offline
- Low latency for small models

## File Structure

```
app/src/main/
├── assets/
│   └── gemma-3-270m-it-int8.task  # Model file (user must download)
├── java/com/iqbalansyor/llm_on_device/
│   ├── MainActivity.kt
│   ├── data/
│   │   ├── ChatRepository.kt       # LLM inference logic
│   │   └── LlmModelManager.kt      # Model file management
│   ├── model/
│   │   └── ChatMessage.kt          # Message data class
│   └── ui/
│       ├── ChatScreen.kt           # Main UI
│       ├── ChatViewModel.kt        # UI state management
│       └── components/
│           ├── ChatInput.kt        # Text input field
│           ├── MessageBubble.kt    # Chat message bubble
│           ├── MessageList.kt      # Messages container
│           └── TypingIndicator.kt  # Loading animation
```

## Dependencies

```kotlin
implementation("com.google.mediapipe:tasks-genai:0.10.24")
```

MediaPipe Tasks GenAI includes:
- TFLite runtime
- LLM inference engine
- GPU delegate support (automatic)