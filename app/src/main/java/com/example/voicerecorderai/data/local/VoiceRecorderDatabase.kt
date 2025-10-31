package com.example.voicerecorderai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.voicerecorderai.data.local.dao.AudioChunkDao
import com.example.voicerecorderai.data.local.dao.RecordingDao
import com.example.voicerecorderai.data.local.entity.AudioChunkEntity
import com.example.voicerecorderai.data.local.entity.RecordingEntity

@Database(
    entities = [RecordingEntity::class, AudioChunkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoiceRecorderDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun audioChunkDao(): AudioChunkDao
}

