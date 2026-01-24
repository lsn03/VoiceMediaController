package ru.lsn03.voicemediacontroller.service

import android.os.SystemClock
import android.util.Log
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import ru.lsn03.voicemediacontroller.vosk.VoskResult

class VoiceCoordinator(
    private val vosk: VoskEngine,
    private val soundPoolProvider: SoundPoolProvider,
    private val handleCommandText: (String) -> Unit,
    private val publishRecognizedText: (String) -> Unit,
    private val switchToCommandModeInternal: () -> Unit,
    private val resetToWakeModeInternal: () -> Unit,

    ) {

    @Volatile
    private var pendingResetToWake = false

    @Volatile
    private var pendingSwitchToCommand = false

    private var isListeningCommand = false
    private var lastUiUpdateMs = 0L
    private var lastWakeTriggerMs = 0L
    private val UI_THROTTLE_MS = 250L        // не чаще 4 раз/сек
    private val WAKE_DEBOUNCE_MS = 1200L

    fun onPcm(pcm: ByteArray) {

        val now = SystemClock.elapsedRealtime()

        if (pendingResetToWake) {
            pendingResetToWake = false
            Log.d(APPLICATION_NAME, "handlePcm: pendingResetToWake")
            isListeningCommand = false             // <-- ВАЖНО
            resetToWakeModeInternal()
            return
        }


        if (pendingSwitchToCommand) {
            pendingSwitchToCommand = false
            Log.d(APPLICATION_NAME, "handlePcm: pendingSwitchToCommand")
            isListeningCommand = true              // <-- ВАЖНО
            switchToCommandModeInternal()
        }


        if (isListeningCommand) {
            // ---------- COMMAND MODE ----------
            when (val r = vosk.acceptCommand(pcm)) {
                is VoskResult.Final -> {
                    Log.d(APPLICATION_NAME, "VoiceService:: CMD final: ${r.text}")
                    handleCommandText(r.text)
                    resetToWakeMode() // <-- чтобы выйти из команд и отпустить ducking
                }

                is VoskResult.Partial -> {
                    val partialText = r.text.trim()
                    if (partialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                        lastUiUpdateMs = now
                        publishRecognizedText("Команда: $partialText")
                    }
                }

                VoskResult.None -> Unit
            }

        } else {
            // ---------- WAKE MODE ----------
            when (val r = vosk.acceptWake(pcm)) {
                is VoskResult.Final -> {
                    val txt = normalize(r.text)
                    Log.d(APPLICATION_NAME, "VoiceService:: WAKE final: $txt")

                    when {
                        txt == "джарвис" -> {
                            val now2 = SystemClock.elapsedRealtime()
                            if (now2 - lastWakeTriggerMs >= WAKE_DEBOUNCE_MS) {
                                lastWakeTriggerMs = now2
                                publishRecognizedText("Джарвис! Слушаю команду...")
                                soundPoolProvider.playHappy()
                                switchToCommandMode()
                            } else {
                                Log.d(APPLICATION_NAME, "Wake debounce: ignored")
                            }
                        }

                        txt.startsWith("джарвис ") -> {
                            soundPoolProvider.playHappy()
                            val cmd = txt.removePrefix("джарвис ").trim()
                            Log.d(APPLICATION_NAME, "VoiceService:: WAKE cmd: $cmd")
                            publishRecognizedText("Выполняю: $cmd")

                            handleCommandText(cmd)

                            // остаёмся в WAKE (а handleCommandText сам поставит pendingResetToWake
                            // если ты внутри него вызываешь resetToWakeMode())
                            vosk.resetWake()
                        }

                        else -> {
                            // не наша фраза
                            vosk.resetWake()
                        }
                    }
                }

                is VoskResult.Partial -> {
                    val wakePartialText = r.text.trim()
                    if (wakePartialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                        lastUiUpdateMs = now
                        publishRecognizedText("Слышу: $wakePartialText")
                    }
                }

                VoskResult.None -> Unit
            }
        }


    }

    fun normalize(s: String) = s.trim().lowercase()

    private fun switchToCommandMode() {
        pendingSwitchToCommand = true
    }

    fun resetToWakeMode() {
        pendingResetToWake = true
    }

}
