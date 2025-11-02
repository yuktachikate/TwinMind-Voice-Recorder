package com.example.voicerecorderai.util

import android.content.Context

/**
 * Configuration manager that loads API keys from assets at runtime
 * instead of embedding them in BuildConfig.
 *
 * IMPORTANT: This is more secure than BuildConfig, but still not perfect.
 * For production apps, use one of these approaches:
 * 1. Backend proxy server (API key stays on server)
 * 2. NDK native library with obfuscation
 * 3. Firebase Remote Config with additional security
 * 4. Android Keystore + encrypted preferences
 */
object SecureConfig {

    private const val CONFIG_FILE = "api_config.properties"

    /**
     * Loads OpenAI API key from assets/api_config.properties at runtime.
     * This prevents the key from being embedded as a string constant in the APK.
     */
    fun getOpenAIApiKey(context: Context): String? {
        return try {
            val properties = java.util.Properties()
            context.assets.open(CONFIG_FILE).use { stream ->
                properties.load(stream)
            }
            properties.getProperty("openai.api.key")
        } catch (e: Exception) {
            android.util.Log.w("SecureConfig", "Failed to load API key from assets", e)
            // Return null to fallback to mock service
            null
        }
    }
}

