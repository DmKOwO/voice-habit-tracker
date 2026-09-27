package com.voicehabit.tracker.domain.repository

import com.voicehabit.tracker.domain.model.VoiceNoteAction
import java.io.File

interface VoiceRepository {
    suspend fun processVoiceAudio(
        audioFile: File,
        clientCurrentTimeIso: String,
        timezone: String,
        spokenTranscript: String? = null,
        /** H1. Длительность речи: попадает в конспект, чтобы «5:20 разговора → 4 тезиса». */
        speechSeconds: Int = 0
    ): Result<VoiceNoteAction>

    suspend fun processVoiceText(
        transcript: String,
        clientCurrentTimeIso: String,
        timezone: String,
        speechSeconds: Int = 0
    ): Result<VoiceNoteAction>

    suspend fun saveVoiceLog(
        audioPath: String,
        transcript: String,
        summary: String,
        status: String
    ): String
}
