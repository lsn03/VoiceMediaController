package ru.lsn03.voicemediacontroller.voice

enum class VoiceStateEvent { WakeHeard, CommandRecognised, CommandTimeout, Reset }

/**
 * Pure state machine. SWITCHING is intentionally absent: it is a coordinator-local
 * transient flag used while side-effects run during a transition; the SM itself only
 * exposes the stable logical states.
 *
 * Invalid events return the current state unchanged (no throw, no logging). The
 * coordinator observes the no-op and decides whether to log.
 */
class VoiceStateMachine(initial: VoiceState = VoiceState.WAKE_LISTENING) {

    @Volatile
    private var current: VoiceState = initial

    val state: VoiceState get() = current

    fun on(event: VoiceStateEvent): VoiceState {
        val next = when (event) {
            VoiceStateEvent.Reset -> VoiceState.WAKE_LISTENING
            VoiceStateEvent.WakeHeard ->
                if (current == VoiceState.WAKE_LISTENING) VoiceState.COMMAND_LISTENING else current
            VoiceStateEvent.CommandRecognised ->
                if (current == VoiceState.COMMAND_LISTENING) VoiceState.WAKE_LISTENING else current
            VoiceStateEvent.CommandTimeout ->
                if (current == VoiceState.COMMAND_LISTENING) VoiceState.WAKE_LISTENING else current
        }
        current = next
        return next
    }
}
