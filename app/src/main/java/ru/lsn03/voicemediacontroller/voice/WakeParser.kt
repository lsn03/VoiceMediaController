package ru.lsn03.voicemediacontroller.voice

sealed interface WakeOutcome {
    data object Nothing : WakeOutcome
    data class WakeHeard(val titleCased: String) : WakeOutcome
    data class WakeThenCommand(val command: String) : WakeOutcome
    data class HeardPartial(val text: String) : WakeOutcome
}

class WakeParser(private val wakeWord: () -> String) {

    fun parseFinal(normalizedText: String): WakeOutcome {
        val wake = wakeWord()
        return when {
            normalizedText == wake -> WakeOutcome.WakeHeard(wake.replaceFirstChar { it.uppercase() })
            normalizedText.startsWith("$wake ") -> {
                val cmd = normalizedText.removePrefix("$wake ").trim()
                if (cmd.isEmpty()) WakeOutcome.Nothing else WakeOutcome.WakeThenCommand(cmd)
            }
            else -> WakeOutcome.Nothing
        }
    }

    fun parsePartial(partialText: String): WakeOutcome {
        val trimmed = partialText.trim()
        return if (trimmed.isEmpty()) WakeOutcome.Nothing else WakeOutcome.HeardPartial(trimmed)
    }
}
