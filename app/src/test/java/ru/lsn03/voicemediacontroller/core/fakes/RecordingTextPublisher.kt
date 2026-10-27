package ru.lsn03.voicemediacontroller.core.fakes

import ru.lsn03.voicemediacontroller.core.text.TextPublisher
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [TextPublisher] that records every published string in call order.
 *
 * Tests inspect [published] to assert both WHAT was published and in WHICH
 * order. Backed by a [CopyOnWriteArrayList] so publishes from a background
 * thread (e.g. the audio callback thread exercised in `VoiceCoordinator`
 * scenarios) remain visible to the test thread without extra synchronisation.
 *
 * Zero Android dependencies.
 */
class RecordingTextPublisher : TextPublisher {

    private val _published: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()

    val published: List<String>
        get() = _published.toList()

    override fun publish(text: String) {
        _published.add(text)
    }

    /** Reset the recorded buffer so the same instance can be reused across phases. */
    fun clear() {
        _published.clear()
    }
}
