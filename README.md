# LLM on Device Android

A simple Android chat app that runs Gemma 3 LLM locally on your device using MediaPipe.

## Features

- On-device LLM inference (no internet required for chat)
- Powered by Google's Gemma 3 model
- Clean Material 3 UI

## Setup

### 1. Download the Model

Download the Gemma 3 model from Kaggle:

1. Go to [Gemma 3 on Kaggle](https://www.kaggle.com/models/google/gemma-3/tfLite)
2. Select **gemma-3-270m-it-int8** variant
3. Download the `.task` file

### 2. Add Model to Project

Place the downloaded model file in:
```
app/src/main/assets/gemma-3-270m-it-int8.task
```

Create the `assets` folder if it doesn't exist.

### 3. Build and Run

Open the project in Android Studio and run on your device.

## Requirements

- Android SDK 24+
- ~300MB storage for the model
- Device with sufficient RAM (recommended 4GB+)

## License

MIT