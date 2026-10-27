package ru.lsn03.voicemediacontroller.voice

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.db.VoicePhraseEntity
import ru.lsn03.voicemediacontroller.db.VoiceWakePhraseSettingEntity
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.voice.repo.GrammarRepositoryImpl
import ru.lsn03.voicemediacontroller.voice.repo.VoicePhraseRepositoryImpl
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepositoryImpl

/** Characterization: pins JSON output of GrammarRepository / GrammarBuilder (post-PR 4 split). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class GrammarBuilderCharacterizationTest {

    /**
     * Pins the three JSON strings produced by `GrammarRepositoryImpl.observeGrammars()`
     * which delegates to `GrammarBuilder.build()`. Robolectric provides the real
     * Android `org.json.JSONObject.quote` implementation used inside `GrammarBuilder`.
     *
     * The Flow uses `.debounce(300)` — we drive virtual time via `runTest`.
     */

    private val normalizer = TextNormalizer()
    private val logger = RecordingLogger()
    private val grammarBuilder = GrammarBuilder(normalizer, logger)

    @Test
    fun observeGrammars_twoPhrasesAndWakeWord_emitsExpectedJson() = runTest {
        val dao = mockk<VoiceCommandDao>()
        every { dao.observeSettings() } returns flowOf(
            VoiceWakePhraseSettingEntity(id = 1, wakeWord = "джарвис")
        )
        every { dao.observeEnabledPhrases() } returns flowOf(
            listOf(
                VoicePhraseEntity(
                    id = 1,
                    action = "NEXT",
                    phrase = "следующий трек",
                    normalized = "следующий трек",
                    enabled = true
                ),
                VoicePhraseEntity(
                    id = 2,
                    action = "PREV",
                    phrase = "предыдущий трек",
                    normalized = "предыдущий трек",
                    enabled = true
                )
            )
        )

        val phraseRepo = VoicePhraseRepositoryImpl(dao, normalizer, logger)
        val wakeRepo = WakeWordRepositoryImpl(dao, normalizer)
        val grammarRepo = GrammarRepositoryImpl(phraseRepo, wakeRepo, grammarBuilder)

        grammarRepo.observeGrammars().test {
            val g = awaitItem()
            assertThat(g.wakeWordGrammarJson)
                .isEqualTo("[\"джарвис\",\"[unk]\"]")
            assertThat(g.commandGrammarJson)
                .isEqualTo("[\"следующий трек\",\"предыдущий трек\"]")
            assertThat(g.wakeCommandGrammarJson)
                .isEqualTo("[\"джарвис следующий трек\",\"джарвис предыдущий трек\",\"[unk]\"]")
            awaitComplete()
        }
    }

    @Test
    fun observeGrammars_emptyPhraseList_emitsUnkFallbackJson() = runTest {
        val dao = mockk<VoiceCommandDao>()
        every { dao.observeSettings() } returns flowOf(
            VoiceWakePhraseSettingEntity(id = 1, wakeWord = "джарвис")
        )
        every { dao.observeEnabledPhrases() } returns flowOf(emptyList())

        val phraseRepo = VoicePhraseRepositoryImpl(dao, normalizer, logger)
        val wakeRepo = WakeWordRepositoryImpl(dao, normalizer)
        val grammarRepo = GrammarRepositoryImpl(phraseRepo, wakeRepo, grammarBuilder)

        grammarRepo.observeGrammars().test {
            val g = awaitItem()
            assertThat(g.wakeWordGrammarJson)
                .isEqualTo("[\"джарвис\",\"[unk]\"]")
            assertThat(g.commandGrammarJson)
                .isEqualTo("[\"[unk]\"]")
            assertThat(g.wakeCommandGrammarJson)
                .isEqualTo("[\"[unk]\"]")
            awaitComplete()
        }
    }
}
