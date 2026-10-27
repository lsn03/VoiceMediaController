package ru.lsn03.voicemediacontroller.voice

import kotlinx.coroutines.CoroutineScope

/**
 * In-memory [VoiceEffects] that records every call in insertion order so tests
 * can assert both the sequence and the arguments. Hand-written: we never mock
 * types we own.
 */
class RecordingVoiceEffects : VoiceEffects {

    data class Call(val kind: String, val arg: String?)

    private val _calls: MutableList<Call> = mutableListOf()

    val calls: List<Call>
        get() = _calls.toList()

    override fun start(scope: CoroutineScope) {
        _calls.add(Call("start", null))
    }

    override fun publishText(text: String) {
        _calls.add(Call("publishText", text))
    }

    override fun onFinalCommand(text: String) {
        _calls.add(Call("onFinalCommand", text))
    }

    override fun enterWake() {
        _calls.add(Call("enterWake", null))
    }

    override fun enterCommand() {
        _calls.add(Call("enterCommand", null))
    }

    fun clear() {
        _calls.clear()
    }
}
