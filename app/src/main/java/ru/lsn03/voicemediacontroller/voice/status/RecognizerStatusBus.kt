package ru.lsn03.voicemediacontroller.voice.status

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.lsn03.voicemediacontroller.core.text.TextPublisher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-singleton bus carrying the latest recognizer status.
 * `MutableStateFlow` is thread-safe for concurrent writes: `publish` may
 * be invoked from the audio callback thread (via `VoiceEffectsImpl`),
 * while `post` is invoked from service / effects threads holding typed
 * status.
 */
@Singleton
class RecognizerStatusBus @Inject constructor() : TextPublisher {

    private val _status = MutableStateFlow<RecognizerStatus>(RecognizerStatus.Idle)
    val status: StateFlow<RecognizerStatus> = _status.asStateFlow()

    /**
     * Narrow `TextPublisher` entry point: preserves the legacy single-text
     * channel by wrapping the raw string as `WakeHint`. Callers that know
     * the semantic status should use [post] instead.
     */
    override fun publish(text: String) {
        _status.value = RecognizerStatus.WakeHint(text)
    }

    fun post(status: RecognizerStatus) {
        _status.value = status
    }
}
