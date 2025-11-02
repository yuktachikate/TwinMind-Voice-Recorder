package com.example.voicerecorderai.di

import android.content.Context
import android.util.Log
import com.example.voicerecorderai.data.remote.MockApiService
import com.example.voicerecorderai.data.remote.OpenAIApiService
import com.example.voicerecorderai.data.remote.VoiceApi
import com.example.voicerecorderai.util.SecureConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "NetworkModule"

    @Provides
    @Singleton
    fun provideVoiceApi(@ApplicationContext context: Context): VoiceApi {
        return try {
            // Load API key at runtime from assets (not embedded in BuildConfig)
            val apiKey = SecureConfig.getOpenAIApiKey(context)

            if (!apiKey.isNullOrEmpty() && apiKey != "YOUR_API_KEY_HERE") {
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

