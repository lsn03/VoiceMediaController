package ru.lsn03.voicemediacontroller.voice.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.db.VoiceWakePhraseSettingEntity
import ru.lsn03.voicemediacontroller.text.TextNormalizer

interface WakeWordRepository {
    fun observe(): Flow<String>
    suspend fun set(newWord: String)
    /** Snapshot last-known value for callers (e.g. WakeParser lambda on the audio thread). */
    fun current(): String

    companion object {
        const val DEFAULT_WAKE_WORD = "джарвис"
    }
}

class WakeWordRepositoryImpl(
    private val dao: VoiceCommandDao,
    private val normalizer: TextNormalizer,
) : WakeWordRepository {

    // Written by the observe() collector and set(); read synchronously from the audio
    // callback thread via WakeParser. String reference assignment is atomic; @Volatile
    // ensures visibility across threads.
    @Volatile
    private var cached: String = WakeWordRepository.DEFAULT_WAKE_WORD

    override fun observe(): Flow<String> =
        dao.observeSettings()
            .map { entity ->
                val raw = entity?.wakeWord ?: ""
                normalizer.normalize(raw).ifBlank { WakeWordRepository.DEFAULT_WAKE_WORD }
            }
            .onEach { cached = it }

    override suspend fun set(newWord: String) {
        val normalized = normalizer.normalize(newWord)
        val effective = normalized.ifBlank { WakeWordRepository.DEFAULT_WAKE_WORD }
        cached = effective
        dao.upsertSettings(VoiceWakePhraseSettingEntity(wakeWord = effective))
    }

    override fun current(): String = cached
}
