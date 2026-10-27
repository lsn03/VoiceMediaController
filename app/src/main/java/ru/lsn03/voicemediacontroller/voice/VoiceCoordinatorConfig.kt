package ru.lsn03.voicemediacontroller.voice

data class VoiceCoordinatorConfig(
    val uiThrottleMs: Long,
    val wakeDebounceMs: Long,
    val commandTimeoutMs: Long,
) {
    companion object {
        fun default() = VoiceCoordinatorConfig(
            uiThrottleMs = 250L,
            wakeDebounceMs = 1200L,
            commandTimeoutMs = 10_000L,
        )
    }
}
