package com.voicehabit.tracker.domain.usecase

import com.voicehabit.tracker.domain.model.VoiceNoteAction
import com.voicehabit.tracker.domain.repository.VoiceRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ProcessVoiceUseCase(
    private val voiceRepository: VoiceRepository
) {
    suspend operator fun invoke(
        audioFile: File,
        spokenTranscript: String? = null,
        speechSeconds: Int = 0
    ): Result<VoiceNoteAction> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val clientTimeIso = dateFormat.format(Date())
        val tzId = TimeZone.getDefault().id

        return voiceRepository.processVoiceAudio(
            audioFile = audioFile,
            clientCurrentTimeIso = clientTimeIso,
            timezone = tzId,
            spokenTranscript = spokenTranscript,
            speechSeconds = speechSeconds
        )
    }

    suspend fun processText(transcript: String, speechSeconds: Int = 0): Result<VoiceNoteAction> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val clientTimeIso = dateFormat.format(Date())
        val tzId = TimeZone.getDefault().id

        return voiceRepository.processVoiceText(
            transcript = transcript,
            clientCurrentTimeIso = clientTimeIso,
            timezone = tzId,
            speechSeconds = speechSeconds
        )
    }
}
