# Voice Recorder AI

A smart voice recorder application for Android that records audio, transcribes it using AI, and provides a structured summary of the conversation. This project was built as a take-home assignment, demonstrating robust engineering and handling of real-world edge cases.

## Live Demo

A screen recording demonstrating the complete app flow is provided below:

### Example of how Recording/Transcribing and Summarization Works

https://github.com/user-attachments/assets/30416a70-494f-4549-9400-bc7d43f5d15b

### Example of Summary Page
 

https://github.com/user-attachments/assets/cd2eace5-70df-46bb-9b8b-b951c279b296



## Key Features

### 1. Robust Audio Recording
The app is designed for reliable audio capture in various real-world scenarios.

*   **Background Recording:** A foreground service ensures recording continues even when the app is not in the foreground.
*   **Chunk-Based Saving:** Audio is recorded in 30-second chunks with a 2-second overlap. This prevents data loss and allows for real-time processing.
*   **Live Lock Screen Updates:** Provides live recording status on the lock screen, including a timer, status, and Pause/Stop actions.

### 2. Comprehensive Edge Case Handling
The recording engine is built to be resilient to interruptions and system events.

*   **Phone Call Interruptions:** Automatically pauses recording during a phone call and resumes once the call ends.
*   **Audio Focus Management:** Pauses when another app requests audio focus and provides options to resume or stop.
*   **Dynamic Microphone Switching:** Seamlessly continues recording when a user connects or disconnects a Bluetooth or wired headset.
*   **Low Storage Detection:** Proactively checks for available storage and stops gracefully if the device runs out of space.
*   **Process Death Recovery:** Session state is persisted using Room, and a background worker ensures that transcription can resume even if the app process is killed.
*   **Silent Audio Detection:** Monitors for silent audio input and warns the user if no audio is detected for 10 seconds.

### 3. AI-Powered Transcription
Leverages state-of-the-art AI to convert speech to text with high accuracy.

*   **Real-time Transcription:** Audio chunks are uploaded for transcription as soon as they are recorded.
*   **Fault-Tolerant:** Implements a robust retry mechanism to handle network failures, ensuring no audio data is lost.
*   **Persistent Storage:** Transcripts are saved to a local Room database, serving as the single source of truth.

### 4. Intelligent Summarization
The app goes beyond transcription to provide actionable insights from the conversation.

*   **Structured Summaries:** Generates a title, a concise summary, a list of action items, and key discussion points.
*   **Streaming UI:** The summary is streamed to the UI as it's generated, providing a responsive user experience.
*   **Background Operation:** Summary generation runs as a background task, ensuring it completes even if the user navigates away from the app.

## Tech Stack & Architecture

*   **Kotlin:** The entire application is written in modern, idiomatic Kotlin.
*   **Jetpack Compose:** The UI is built 100% with Jetpack Compose for a declarative and modern user interface.
*   **MVVM Architecture:** Follows the Model-View-ViewModel pattern to create a scalable and maintainable codebase.
*   **Coroutines & Flow:** Used for managing all asynchronous operations, from recording to API calls.
*   **Hilt:** For robust dependency injection.
*   **Room:** For local, persistent storage of recordings, transcripts, and summaries.
*   **Retrofit:** For efficient and type-safe communication with the transcription and summarization APIs.
*   **Foreground Services & Workers:** For handling background recording and ensuring reliable task completion.

## How to Build

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/your-username/voicerecorderai.git
    ```
2.  **Open in Android Studio**
    Open the project in the latest version of Android Studio.

3.  **Add API Key**
    Create a `local.properties` file in the root directory and add your API key:
    ```
    sdk.dir=<path-to-your-android-sdk>
    openai.api.key=<openai-your-api-key>
    ```
4.  **Build and Run**
    Build the project and run it on an Android device or emulator (API 24+).

## Contributing

This project was developed for a take-home assignment, but contributions and suggestions are always welcome. Please feel free to open an issue or submit a pull request.

