package ru.lsn03.voicemediacontroller.voice

import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import ru.lsn03.voicemediacontroller.action.ActionExecutor
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.core.fakes.RecordingTextPublisher
import ru.lsn03.voicemediacontroller.db.VoicePhraseEntity
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.voice.repo.VoicePhraseRepository
import ru.lsn03.voicemediacontroller.vosk.VoskEngine

/**
 * Behavioural pin for [VoiceEffectsImpl] after the PR 5 migration off
 * `LocalBroadcastManager`.
 *
 * Every text event now flows through the injected [TextPublisher]
 * ([RecordingTextPublisher] here) — not an [android.content.Intent] —
 * so the test can run on the JVM without Robolectric. The three Android
 * seams the class still holds (`VoskEngine`, `SoundPoolProvider`,
 * `ActionExecutorProvider`) are mocked via MockK; their `Context`
 * dependencies never materialise.
 *
 * Scenarios covered:
 *   1. `enterWake` publishes "Слушаю..." and ducks + resets as expected.
 *   2. `enterCommand` publishes "Слушаю команду..." and ducks + resets.
 *   3. `onFinalCommand` with a known phrase publishes "Выполняю: $cmd",
 *      resolves the right action and executes it.
 *   4. `onFinalCommand` with an unknown phrase falls back to UNKNOWN and
 *      still publishes "Выполняю: ...".
 *   5. `onFinalCommand` normalises input (trim + lowercase + collapse
 *      whitespace) before matching and before publishing.
 *   6. `onFinalCommand` with blank text short-circuits — no publish,
 *      no executor lookup.
 *   7. `publishText` is a thin passthrough to the injected publisher.
 */
class VoiceEffectsImplTest {

    private class RecordingAudioDucker : AudioDucker {
        var startCalls: Int = 0
            private set
        var stopCalls: Int = 0
            private set

        override fun start() { startCalls += 1 }
        override fun stop() { stopCalls += 1 }
    }

    private class StubPhraseRepository(
        private val bindings: Flow<List<CommandBinding>> = MutableStateFlow(emptyList()),
    ) : VoicePhraseRepository {
        override fun observePhrases(action: VoiceAction): Flow<List<VoicePhraseEntity>> =
            MutableStateFlow(emptyList())
        override fun observeAllEnabled(): Flow<List<VoicePhraseEntity>> =
            MutableStateFlow(emptyList())
        override suspend fun addPhrase(action: VoiceAction, phrase: String) = Result.success(Unit)
        override suspend fun setPhraseEnabled(id: Long, enabled: Boolean) = Result.success(Unit)
        override suspend fun deletePhrase(id: Long) = Result.success(Unit)
        override suspend fun updatePhraseText(id: Long, newText: String) = Result.success(Unit)
        override suspend fun resetToDefaults(action: VoiceAction) = Result.success(Unit)
        override fun observeBindings(): Flow<List<CommandBinding>> = bindings
    }

    private class Fixture(
        bindings: List<CommandBinding> = emptyList(),
    ) {
        val vosk: VoskEngine = mockk(relaxed = true)
        val audioDucker = RecordingAudioDucker()
        val soundPool: SoundPoolProvider = mockk(relaxed = true)
        val publisher = RecordingTextPublisher()
        val normalizer = TextNormalizer()
        val phraseRepo = StubPhraseRepository(bindings = MutableStateFlow(bindings))
        val executor: ActionExecutor = mockk()
        val executorProvider: ActionExecutorProvider = mockk()

        init {
            every { executor.execute() } just Runs
            every { executorProvider.getExecutor(any()) } returns executor
        }

        val effects = VoiceEffectsImpl(
            actionExecutorProvider = executorProvider,
            audioDucker = audioDucker,
            vosk = vosk,
            soundPoolProvider = soundPool,
            phraseRepo = phraseRepo,
            normalizer = normalizer,
            publisher = publisher,
        )

        /**
         * Install a non-empty [CommandMatcher] directly on [effects] via the
         * same path the production `start(scope)` collector uses. Keeps the
         * test off the coroutines-collect critical path; we only care about
         * the matcher state VoiceEffectsImpl ends up with.
         */
        fun installMatcher(bindings: List<CommandBinding>) {
            val field = VoiceEffectsImpl::class.java.getDeclaredField("matcher")
            field.isAccessible = true
            field.set(
                effects,
                ru.lsn03.voicemediacontroller.command.CommandMatcher(bindings),
            )
        }
    }

    @Test
    fun enterWake_publishesListeningText_resetsRecognizersAndStopsDucker() {
        val f = Fixture()

        f.effects.enterWake()

        assertThat(f.publisher.published).containsExactly("Слушаю...")
        assertThat(f.audioDucker.stopCalls).isEqualTo(1)
        assertThat(f.audioDucker.startCalls).isEqualTo(0)
        verify(exactly = 1) { f.vosk.resetWake() }
        verify(exactly = 1) { f.vosk.resetCommand() }
        verify(exactly = 1) { f.soundPool.playSad() }
    }

    @Test
    fun enterCommand_publishesListeningCommandText_startsDuckerAndResetsCommand() {
        val f = Fixture()

        f.effects.enterCommand()

        assertThat(f.publisher.published).containsExactly("Слушаю команду...")
        assertThat(f.audioDucker.startCalls).isEqualTo(1)
        assertThat(f.audioDucker.stopCalls).isEqualTo(0)
        verify(exactly = 1) { f.vosk.resetCommand() }
        verify(exactly = 0) { f.vosk.resetWake() }
        verify(exactly = 1) { f.soundPool.playHappy() }
    }

    @Test
    fun onFinalCommand_withMatchingPhrase_publishesExecutingAndRunsExecutor() {
        val bindings = listOf(
            CommandBinding(phrases = listOf("следующий трек"), action = VoiceAction.NEXT),
        )
        val f = Fixture()
        f.installMatcher(bindings)

        f.effects.onFinalCommand("следующий трек")

        assertThat(f.publisher.published).containsExactly("Выполняю: следующий трек")
        verify(exactly = 1) { f.executorProvider.getExecutor(VoiceAction.NEXT) }
        verify(exactly = 1) { f.executor.execute() }
    }

    @Test
    fun onFinalCommand_withUnmatchedPhrase_fallsBackToUnknownButStillPublishes() {
        val f = Fixture()
        // No bindings installed, so matcher.match returns null → UNKNOWN.

        f.effects.onFinalCommand("прочитай что-нибудь")

        assertThat(f.publisher.published).containsExactly("Выполняю: прочитай что-нибудь")
        verify(exactly = 1) { f.executorProvider.getExecutor(VoiceAction.UNKNOWN) }
        verify(exactly = 1) { f.executor.execute() }
    }

    @Test
    fun onFinalCommand_withMessyWhitespaceAndCasing_normalisesBeforeMatching() {
        // Normalization contract from TextNormalizer: trim + lowercase + collapse `\s+`.
        // The matcher is populated with the normalised form only, so a raw
        // "  СЛЕДУЮЩИЙ   трек  " must still resolve to NEXT.
        val bindings = listOf(
            CommandBinding(phrases = listOf("следующий трек"), action = VoiceAction.NEXT),
        )
        val f = Fixture()
        f.installMatcher(bindings)

        f.effects.onFinalCommand("  СЛЕДУЮЩИЙ   трек  ")

        assertThat(f.publisher.published).containsExactly("Выполняю: следующий трек")
        verify(exactly = 1) { f.executorProvider.getExecutor(VoiceAction.NEXT) }
    }

    @Test
    fun onFinalCommand_withBlankText_shortCircuitsWithoutPublishOrExecute() {
        val f = Fixture()

        f.effects.onFinalCommand("   ")

        assertThat(f.publisher.published).isEmpty()
        verify(exactly = 0) { f.executorProvider.getExecutor(any()) }
        verify(exactly = 0) { f.executor.execute() }
    }

    @Test
    fun publishText_passesThroughToInjectedPublisherVerbatim() {
        val f = Fixture()

        f.effects.publishText("Выполняю: тест")
        f.effects.publishText("Жду команду")

        assertThat(f.publisher.published)
            .containsExactly("Выполняю: тест", "Жду команду")
            .inOrder()
    }
}
