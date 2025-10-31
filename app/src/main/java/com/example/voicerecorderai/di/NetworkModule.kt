package com.example.voicerecorderai.di

import android.util.Log
import com.example.voicerecorderai.BuildConfig
import com.example.voicerecorderai.data.remote.MockApiService
import com.example.voicerecorderai.data.remote.OpenAIApiService
import com.example.voicerecorderai.data.remote.VoiceApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "NetworkModule"

    @Provides
    @Singleton
    fun provideVoiceApi(): VoiceApi {
        return try {
            // Check if OpenAI API key is configured
            val apiKey = BuildConfig.OPENAI_API_KEY

            if (apiKey.isNotEmpty() && apiKey != "YOUR_API_KEY_HERE") {
                Log.d(TAG, "Using OpenAI API Service (Real API)")
                OpenAIApiService(apiKey)
            } else {
                Log.d(TAG, "Using Mock API Service (No API key configured)")
                MockApiService()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize OpenAI service, falling back to Mock", e)
            MockApiService()
        }
    }
}

