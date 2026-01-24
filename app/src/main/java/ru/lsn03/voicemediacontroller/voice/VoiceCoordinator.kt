package ru.lsn03.voicemediacontroller.voice

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import ru.lsn03.voicemediacontroller.vosk.VoskResult

class VoiceCoordinator(
    private val vosk: VoskEngine,
    private val effects: VoiceEffects,
    private val handler: Handler,
) {
    private var lastUiUpdateMs = 0L
    private var lastWakeTriggerMs = 0L
    private val UI_THROTTLE_MS = 250L        // не чаще 4 раз/сек
    private val WAKE_DEBOUNCE_MS = 1200L
    private val commandTimeoutMs: Long = 10_000L

    private var state: VoiceState = VoiceState.WAKE_LISTENING

    private val timeoutRunnable = Runnable { onTimeout() }


    fun onPcm(pcm: ByteArray) {

        val now = SystemClock.elapsedRealtime()

//        Log.d(APPLICATION_NAME, "VoiceCoordinator currentState=$state")
        when (state) {
            VoiceState.SWITCHING -> Unit
            VoiceState.WAKE_LISTENING -> handleWakePcm(pcm, now)
            VoiceState.COMMAND_LISTENING -> handleCommandPcm(pcm, now)
        }

    }

    fun onTimeout() {
        if (state == VoiceState.COMMAND_LISTENING) {
            Log.d(APPLICATION_NAME, "VoiceCoordinator::onTimeout -> WAKE")
            transitionTo(VoiceState.WAKE_LISTENING)
        }
    }

    private fun handleCommandPcm(pcm: ByteArray, now: Long) {
        when (val r = vosk.acceptCommand(pcm)) {
            is VoskResult.Final -> {
                Log.d(APPLICATION_NAME, "VoiceService:: CMD final: ${r.text}")
                effects.onFinalCommand(r.text)
                transitionTo(VoiceState.WAKE_LISTENING)
            }

            is VoskResult.Partial -> {
                val partialText = r.text.trim()
                if (partialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                    lastUiUpdateMs = now
                    effects.publishText("Команда: $partialText")
                }
            }

            VoskResult.None -> Unit
        }
    }

    private fun handleWakePcm(pcm: ByteArray, now: Long) {
        when (val r = vosk.acceptWake(pcm)) {
            is VoskResult.Final -> {
                val txt = normalize(r.text)
                Log.d(APPLICATION_NAME, "VoiceCoordinator::handleWakePcm WAKE final: $txt")

                when {
                    txt == "джарвис" -> {
                        val now2 = SystemClock.elapsedRealtime()
                        if (now2 - lastWakeTriggerMs >= WAKE_DEBOUNCE_MS) {
                            lastWakeTriggerMs = now2
                            effects.publishText("Джарвис! Слушаю команду...")
                            transitionTo(VoiceState.COMMAND_LISTENING)
                        } else {
                            Log.d(APPLICATION_NAME, "Wake debounce: ignored")
                        }
                    }

                    txt.startsWith("джарвис ") -> {
                        val cmd = txt.removePrefix("джарвис ").trim()
                        effects.publishText("Выполняю: $cmd")
                        effects.onFinalCommand(cmd)
                        // Остаёмся в WAKE
                        vosk.resetWake()
                    }

                    else -> {
                        vosk.resetWake()
                    }
                }
            }

            is VoskResult.Partial -> {
                val wakePartial = r.text.trim()
                if (wakePartial.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                    lastUiUpdateMs = now
                    effects.publishText("Слышу: $wakePartial")
                }
            }

            VoskResult.None -> Unit
        }
    }

    private fun transitionTo(target: VoiceState) {
        if (state == target) return

        state = VoiceState.SWITCHING
        when (target) {
            VoiceState.WAKE_LISTENING -> {
                handler.removeCallbacks(timeoutRunnable)
                effects.enterWake()
            }
            VoiceState.COMMAND_LISTENING -> {
                effects.enterCommand()
                handler.removeCallbacks(timeoutRunnable)
                handler.postDelayed(timeoutRunnable, commandTimeoutMs)
            }

            VoiceState.SWITCHING -> Unit
        }
        state = target
    }

    private fun normalize(s: String) = s.trim().lowercase()


}