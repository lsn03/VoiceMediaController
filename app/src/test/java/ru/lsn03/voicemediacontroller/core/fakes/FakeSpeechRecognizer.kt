package ru.lsn03.voicemediacontroller.core.fakes

import ru.lsn03.voicemediacontroller.core.speech.SpeechRecognizer
import ru.lsn03.voicemediacontroller.core.speech.SpeechResult

/**
 * Hand-written fake [SpeechRecognizer] driven by two FIFO queues.
 *
 * Tests pre-stage the sequence of [SpeechResult]s to return via
 * [scheduleWake] / [scheduleCommand] and then drive the recognizer by
 * feeding PCM to [acceptWake] / [acceptCommand]. When a queue is empty,
 * the fake returns [SpeechResult.None], matching the real adapter
 * behaviour when the engine has nothing to report.
 *
 * All grammar updates and reset calls are recorded so tests can assert
 * both the data flow and the control flow.
 */
class FakeSpeechRecognizer : SpeechRecognizer {

    private val wakeQueue: ArrayDeque<SpeechResult> = ArrayDeque()
    private val commandQueue: ArrayDeque<SpeechResult> = ArrayDeque()

    var lastGrammars: Triple<String, String, String>? = null
        private set

    var resetWakeCalls: Int = 0
        private set

    var resetCommandCalls: Int = 0
        private set

    fun scheduleWake(result: SpeechResult) {
        wakeQueue.addLast(result)
    }

    fun scheduleCommand(result: SpeechResult) {
        commandQueue.addLast(result)
    }

    override fun updateGrammars(wakeJson: String, commandJson: String, wakeCommandJson: String) {
        lastGrammars = Triple(wakeJson, commandJson, wakeCommandJson)
    }

    override fun acceptWake(pcm: ByteArray): SpeechResult =
        wakeQueue.removeFirstOrNull() ?: SpeechResult.None

    override fun acceptCommand(pcm: ByteArray): SpeechResult =
        commandQueue.removeFirstOrNull() ?: SpeechResult.None

    override fun resetWake() {
        resetWakeCalls += 1
    }

    override fun resetCommand() {
        resetCommandCalls += 1
    }
}
