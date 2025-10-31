package com.example.voicerecorderai.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Recording : Screen("recording")
    object Summary : Screen("summary/{recordingId}") {
        fun createRoute(recordingId: Long) = "summary/$recordingId"
    }
}

