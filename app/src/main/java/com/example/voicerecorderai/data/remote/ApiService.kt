package com.example.voicerecorderai.data.remote

import com.example.voicerecorderai.data.remote.model.SummaryRequest
import com.example.voicerecorderai.data.remote.model.SummaryResponse
import com.example.voicerecorderai.data.remote.model.TranscriptionRequest
import com.example.voicerecorderai.data.remote.model.TranscriptionResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("transcribe")
    suspend fun transcribeAudio(
        @Body request: TranscriptionRequest
    ): Response<TranscriptionResponse>

    @Multipart
    @POST("transcribe")
    suspend fun transcribeAudioFile(
        @Part file: MultipartBody.Part
    ): Response<TranscriptionResponse>

    @POST("summarize")
    suspend fun generateSummary(
        @Body request: SummaryRequest
    ): Response<SummaryResponse>

    @Streaming
    @POST("summarize/stream")
    suspend fun generateSummaryStream(
        @Body request: SummaryRequest
    ): Response<String>
}

