package com.example.voicerecorderai.di

import android.content.Context
import androidx.room.Room
import com.example.voicerecorderai.data.local.VoiceRecorderDatabase
import com.example.voicerecorderai.data.local.dao.AudioChunkDao
import com.example.voicerecorderai.data.local.dao.RecordingDao
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoiceRecorderDatabase {
        return Room.databaseBuilder(
            context,
            VoiceRecorderDatabase::class.java,
            "voice_recorder_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(database: VoiceRecorderDatabase): RecordingDao {
        return database.recordingDao()
    }

    @Provides
    @Singleton
    fun provideAudioChunkDao(database: VoiceRecorderDatabase): AudioChunkDao {
        return database.audioChunkDao()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}

