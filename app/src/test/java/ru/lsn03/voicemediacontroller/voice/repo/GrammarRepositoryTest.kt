package ru.lsn03.voicemediacontroller.voice.repo

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import ru.lsn03.voicemediacontroller.db.VoicePhraseEntity
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.voice.GrammarBuilder
import ru.lsn03.voicemediacontroller.voice.VoskGrammars

/**
 * Pure JVM would suffice for the Flow plumbing (combine + distinctUntilChanged +
 * debounce), but [GrammarRepositoryImpl] funnels through [GrammarBuilder] which calls
 * `org.json.JSONObject.quote`. On pure JVM that returns `null`, so Robolectric is
 * required to get the real Android `org.json` implementation.
 *
 * Timing is driven by virtual time via `runTest`. The repo uses a 300 ms debounce,
 * so emissions only land after `advanceTimeBy(300)` (or equivalent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class GrammarRepositoryTest {

    private val builderLogger = RecordingLogger()
    private val builder = GrammarBuilder(TextNormalizer(), builderLogger)

    /** Minimal in-file fake — scope is intentionally this test file only. */
    private class FakeWakeWordRepository(initial: String) : WakeWordRepository {
        private val state = MutableStateFlow(initial)
        override fun observe(): Flow<String> = state.asStateFlow()
        override suspend fun set(newWord: String) {
            state.value = newWord
        }
        override fun current(): String = state.value

        suspend fun emit(value: String) {
            state.value = value
        }
    }

    /** Minimal in-file fake — scope is intentionally this test file only. */
    private class FakePhraseRepository(
        initial: List<VoicePhraseEntity> = emptyList()
    ) : VoicePhraseRepository {
        private val enabled = MutableStateFlow(initial)
        override fun observePhrases(action: VoiceAction): Flow<List<VoicePhraseEntity>> =
            enabled.map { all -> all.filter { it.action == action.name } }
        override fun observeAllEnabled(): Flow<List<VoicePhraseEntity>> = enabled.asStateFlow()
        override suspend fun addPhrase(action: VoiceAction, phrase: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun setPhraseEnabled(id: Long, enabled: Boolean): Result<Unit> =
            Result.success(Unit)
        override suspend fun deletePhrase(id: Long): Result<Unit> = Result.success(Unit)
        override suspend fun updatePhraseText(id: Long, newText: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun resetToDefaults(action: VoiceAction): Result<Unit> =
            Result.success(Unit)
        override fun observeBindings(): Flow<List<CommandBinding>> =
            enabled.map { emptyList() }

        suspend fun emit(rows: List<VoicePhraseEntity>) {
            enabled.value = rows
        }
    }

    private fun row(id: Long, action: VoiceAction, text: String): VoicePhraseEntity =
        VoicePhraseEntity(
            id = id,
            action = action.name,
            phrase = text,
            normalized = text,
            enabled = true,
        )

    @Test
    fun observeGrammars_stableInputs_emitsExactlyOneGrammarAfterDebounce() = runTest {
        val wake = FakeWakeWordRepository("джарвис")
        val phrases = FakePhraseRepository(listOf(row(1, VoiceAction.STOP, "стоп")))
        val repo = GrammarRepositoryImpl(phrases, wake, builder)

        repo.observeGrammars().test {
            // Nothing before debounce expires.
            expectNoEvents()
            // Advance past the 300 ms debounce window.
            testScheduler.advanceTimeBy(301)
            testScheduler.runCurrent()

            val g: VoskGrammars = awaitItem()
            assertThat(g.wakeWordGrammarJson).isEqualTo("[\"джарвис\",\"[unk]\"]")
            assertThat(g.commandGrammarJson).isEqualTo("[\"стоп\"]")
            assertThat(g.wakeCommandGrammarJson)
                .isEqualTo("[\"джарвис стоп\",\"[unk]\"]")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeGrammars_identicalInputsRepeated_areCollapsedByDistinctUntilChanged() = runTest {
        val wake = FakeWakeWordRepository("джарвис")
        val phrases = FakePhraseRepository(listOf(row(1, VoiceAction.STOP, "стоп")))
        val repo = GrammarRepositoryImpl(phrases, wake, builder)

        repo.observeGrammars().test {
            testScheduler.advanceTimeBy(301)
            testScheduler.runCurrent()
            val first = awaitItem()

            // Re-emit the SAME values twice; distinctUntilChanged on VoskGrammars equality
            // suppresses them, so no further item should arrive.
            wake.emit("джарвис")
            phrases.emit(listOf(row(1, VoiceAction.STOP, "стоп")))

            testScheduler.advanceTimeBy(1_000)
            testScheduler.runCurrent()

            expectNoEvents()

            // Sanity: previously latched emission remains the real content.
            assertThat(first.commandGrammarJson).isEqualTo("[\"стоп\"]")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeGrammars_tenRapidWakeChangesWithinDebounce_emitOnlyOne() = runTest {
        val wake = FakeWakeWordRepository("джарвис")
        val phrases = FakePhraseRepository(listOf(row(1, VoiceAction.STOP, "стоп")))
        val repo = GrammarRepositoryImpl(phrases, wake, builder)

        repo.observeGrammars().test {
            // Fire 10 wake-word changes well within the 300 ms window.
            repeat(10) { i ->
                wake.emit("wake$i")
                testScheduler.advanceTimeBy(20) // 20 * 10 = 200 ms < 300 ms
                testScheduler.runCurrent()
            }
            // We are still inside the debounce window — nothing emitted yet.
            expectNoEvents()

            // Cross the boundary: one trailing emission (the latest value) lands.
            testScheduler.advanceTimeBy(301)
            testScheduler.runCurrent()

            val only = awaitItem()
            // Last emission had wake = "wake9".
            assertThat(only.wakeWordGrammarJson).contains("wake9")

            // Nothing else even after plenty of idle time.
            testScheduler.advanceTimeBy(2_000)
            testScheduler.runCurrent()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeGrammars_changesSeparatedBeyondDebounce_emitEachNewGrammar() = runTest {
        val wake = FakeWakeWordRepository("джарвис")
        val phrases = FakePhraseRepository(listOf(row(1, VoiceAction.STOP, "стоп")))
        val repo = GrammarRepositoryImpl(phrases, wake, builder)

        repo.observeGrammars().test {
            testScheduler.advanceTimeBy(301)
            testScheduler.runCurrent()
            val first = awaitItem()
            assertThat(first.wakeWordGrammarJson).contains("джарвис")

            // Change wake, let debounce elapse again.
            wake.emit("алиса")
            testScheduler.advanceTimeBy(301)
            testScheduler.runCurrent()
            val second = awaitItem()
            assertThat(second.wakeWordGrammarJson).contains("алиса")

            // Now swap the phrase list.
            phrases.emit(listOf(row(2, VoiceAction.NEXT, "следующий")))
            testScheduler.advanceTimeBy(301)
            testScheduler.runCurrent()
            val third = awaitItem()
            assertThat(third.commandGrammarJson).isEqualTo("[\"следующий\"]")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
