package ru.lsn03.voicemediacontroller.core.speech

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import ru.lsn03.voicemediacontroller.vosk.VoskResult

/**
 * Verifies that [VoskSpeechRecognizer] is a pure 1-to-1 adapter over
 * [VoskEngine] — forwards grammars and PCM, maps VoskResult.* onto
 * SpeechResult.*, and never mutates state of its own.
 *
 * Pure JVM (no Robolectric): mockk's relaxed mock does not invoke the
 * VoskEngine constructor so the `android.*` dependencies in that class
 * are never touched here. Keeping this off Robolectric lets the JaCoCo
 * agent record the adapter's own line coverage (Robolectric's sandbox
 * classloader bypasses the agent for non-Robolectric-shadowed classes).
 */
class VoskSpeechRecognizerAdapterTest {

    private val engine: VoskEngine = mockk(relaxed = true)
    private val recognizer = VoskSpeechRecognizer(engine)

    @Test
    fun updateGrammars_forwardsThreeStringsPositionallyInOrder() {
        recognizer.updateGrammars(
            wakeJson = "WAKE",
            commandJson = "CMD",
            wakeCommandJson = "WAKE_CMD",
        )

        verify(exactly = 1) {
            engine.updateGrammars(
                wakeWordJson = "WAKE",
                commandJson = "CMD",
                wakeCommandJson = "WAKE_CMD",
            )
        }
    }

    @Test
    fun acceptWake_mapsVoskFinalToSpeechFinal() {
        val pcm = byteArrayOf(1, 2, 3)
        every { engine.acceptWake(pcm) } returns VoskResult.Final("джарвис")

        val result = recognizer.acceptWake(pcm)

        assertThat(result).isEqualTo(SpeechResult.Final("джарвис"))
    }

    @Test
    fun acceptWake_mapsVoskPartialToSpeechPartial() {
        val pcm = byteArrayOf(4, 5)
        every { engine.acceptWake(pcm) } returns VoskResult.Partial("джар")

        val result = recognizer.acceptWake(pcm)

        assertThat(result).isEqualTo(SpeechResult.Partial("джар"))
    }

    @Test
    fun acceptWake_mapsVoskNoneToSpeechNone() {
        val pcm = byteArrayOf(0)
        every { engine.acceptWake(pcm) } returns VoskResult.None

        val result = recognizer.acceptWake(pcm)

        assertThat(result).isEqualTo(SpeechResult.None)
    }

    @Test
    fun acceptCommand_mapsAllThreeVoskVariantsToTheMatchingSpeechResults() {
        val pcm1 = byteArrayOf(1)
        val pcm2 = byteArrayOf(2)
        val pcm3 = byteArrayOf(3)
        every { engine.acceptCommand(pcm1) } returns VoskResult.Final("следующий трек")
        every { engine.acceptCommand(pcm2) } returns VoskResult.Partial("след")
        every { engine.acceptCommand(pcm3) } returns VoskResult.None

        assertThat(recognizer.acceptCommand(pcm1)).isEqualTo(SpeechResult.Final("следующий трек"))
        assertThat(recognizer.acceptCommand(pcm2)).isEqualTo(SpeechResult.Partial("след"))
        assertThat(recognizer.acceptCommand(pcm3)).isEqualTo(SpeechResult.None)
    }

    @Test
    fun resetWake_forwardsToEngineResetWake() {
        recognizer.resetWake()

        verify(exactly = 1) { engine.resetWake() }
    }

    @Test
    fun resetCommand_forwardsToEngineResetCommand() {
        recognizer.resetCommand()

        verify(exactly = 1) { engine.resetCommand() }
    }

    @Test
    fun resetWake_doesNotTouchResetCommand_andViceVersa() {
        recognizer.resetWake()
        recognizer.resetCommand()

        verifyOrder {
            engine.resetWake()
            engine.resetCommand()
        }
    }
}
