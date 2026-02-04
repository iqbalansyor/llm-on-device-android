# Tokenization in LLM on Device

This document explains how tokenization works in Large Language Models (LLMs) and how it applies to this app.

## What is Tokenization?

Tokenization is the process of breaking down text into smaller units called **tokens**. LLMs don't read text character by character or word by word - they process tokens.

```
"Hello, how are you?"
       ↓ Tokenization
["Hello", ",", " how", " are", " you", "?"]
       ↓ Token IDs
[15496, 11, 703, 527, 499, 30]
```

## Why Tokenization?

1. **Vocabulary Size**: Instead of learning every possible word, the model learns a fixed vocabulary of tokens (~32K-256K tokens)
2. **Handling Unknown Words**: Rare words are split into subwords that the model knows
3. **Efficiency**: Reduces the sequence length while preserving meaning
4. **Multilingual Support**: Works across languages using shared subword units

## Types of Tokens

### 1. Whole Words
Common words often become single tokens:
```
"the" → [1]
"hello" → [15496]
```

### 2. Subwords
Less common words are split:
```
"tokenization" → ["token", "ization"]
"unhappiness" → ["un", "happiness"]
```

### 3. Special Tokens
Control tokens for the model:
```
<start_of_turn>  - Marks beginning of a speaker's turn
<end_of_turn>    - Marks end of a speaker's turn
<bos>            - Beginning of sequence
<eos>            - End of sequence
<pad>            - Padding token
```

## Tokenization in This App

### Gemma 3 Tokenizer

Gemma uses **SentencePiece** tokenizer with a vocabulary of ~256K tokens. The tokenization happens inside MediaPipe automatically.

### How It Works in Code

```kotlin
// ChatRepository.kt

// 1. Set maximum tokens for response
val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelManager.modelPath)
    .setMaxTokens(1024)  // Max 1024 tokens in response
    .build()

// 2. Format prompt with special tokens
private fun formatPrompt(userMessage: String): String {
    return "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
}

// 3. Send to model (tokenization happens internally)
session.addQueryChunk(prompt)
session.generateResponse()
```

### The Flow

```
User Input: "What is AI?"
        ↓
Format Prompt: "<start_of_turn>user\nWhat is AI?<end_of_turn>\n<start_of_turn>model\n"
        ↓
Tokenize (internal): [<start_of_turn>, user, \n, What, is, AI, ?, <end_of_turn>, ...]
        ↓
Model Processing: Neural network processes token embeddings
        ↓
Generate Tokens: Model outputs tokens one by one
        ↓
Detokenize (internal): Convert tokens back to text
        ↓
Response: "AI stands for Artificial Intelligence..."
```

## Token Limits

### MaxTokens Parameter

```kotlin
.setMaxTokens(1024)
```

This limits the **response length** to 1024 tokens (~750-800 words in English).

### Context Window

The Gemma 3 model has a context window (total input + output tokens it can handle):

| Model | Context Window |
|-------|---------------|
| Gemma 3 270M | 2048 tokens |
| Gemma 3 1B | 8192 tokens |
| Gemma 3 4B | 8192 tokens |

### Token vs Word Approximation

A rough rule of thumb for English:
- **1 token ≈ 0.75 words**
- **1 token ≈ 4 characters**
- **100 tokens ≈ 75 words**

Examples:
| Text | Approximate Tokens |
|------|-------------------|
| "Hello" | 1 token |
| "Hello, world!" | 4 tokens |
| "Artificial Intelligence" | 2-3 tokens |
| A paragraph (100 words) | ~130 tokens |

## Special Tokens in Gemma

### Turn-Based Format

Gemma uses a specific format for conversations:

```
<start_of_turn>user
What is the capital of France?<end_of_turn>
<start_of_turn>model
The capital of France is Paris.<end_of_turn>
```

### Why This Format?

1. **Role Identification**: Model knows who is speaking
2. **Turn Boundaries**: Clear separation between messages
3. **Instruction Following**: Model trained to respond after `<start_of_turn>model\n`

### In Code

```kotlin
private fun formatPrompt(userMessage: String): String {
    return "<start_of_turn>user\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
}
```

The model sees this and knows:
- Everything after `<start_of_turn>user\n` is from the user
- It should generate after `<start_of_turn>model\n`
- Stop when it wants to end its turn

## Sampling Parameters

After tokenization, the model predicts the next token using these parameters:

```kotlin
val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setTopK(40)           // Consider only top 40 most likely tokens
    .setTemperature(0.8f)  // Randomness in selection
    .setRandomSeed(101)    // For reproducibility
    .build()
```

### TopK (40)

Only consider the 40 most probable next tokens:
```
Token probabilities: [the: 0.3, a: 0.2, an: 0.1, this: 0.05, ...]
TopK=40: Only sample from top 40 tokens
```

### Temperature (0.8)

Controls randomness:
- **0.0**: Always pick highest probability (deterministic)
- **0.5**: Balanced
- **1.0**: More random/creative
- **>1.0**: Very random

```
Temperature 0.2: "The capital of France is Paris."
Temperature 0.8: "The capital of France is Paris, a beautiful city known for..."
Temperature 1.5: "The capital of France is Paris! 🗼 What a magnificent place..."
```

## Token Generation Process

```
Step 1: Input tokens → Model → Probability distribution over vocabulary
Step 2: Sample next token based on TopK and Temperature
Step 3: Add token to sequence
Step 4: Repeat until <end_of_turn> or MaxTokens reached
Step 5: Convert tokens back to text
```

## MediaPipe Abstraction

In this app, MediaPipe handles tokenization internally:

```
┌─────────────────────────────────────────────────┐
│                Your Code                         │
│  session.addQueryChunk("Hello")                 │
│  session.generateResponse()                      │
└─────────────────────┬───────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────┐
│              MediaPipe (Internal)                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────┐ │
│  │  Tokenizer  │→ │   Model     │→ │Detokenize│ │
│  │(SentencePiece) │ (TFLite)    │  │         │ │
│  └─────────────┘  └─────────────┘  └─────────┘ │
└─────────────────────────────────────────────────┘
```

You don't need to manually tokenize - MediaPipe does it for you!

## Key Takeaways

1. **Tokens ≠ Words**: Tokens are subword units
2. **MaxTokens**: Limits response length (1024 in this app)
3. **Special Tokens**: `<start_of_turn>` and `<end_of_turn>` control conversation flow
4. **Automatic**: MediaPipe handles tokenization internally
5. **Parameters**: TopK and Temperature affect how tokens are selected
