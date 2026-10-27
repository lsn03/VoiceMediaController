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
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.db.VoicePhraseEntity
import ru.lsn03.voicemediacontroller.db.VoiceWakePhraseSettingEntity

/** Characterization: PR 3/4 may update assertions when consolidating normalize/grammar builders. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class GrammarBuilderCharacterizationTest {

    /**
     * Pins the three JSON strings produced today by
     * `VoiceCommandRepository.observeVoskGrammars()` + the private `toVoskJsonArray()`.
     *
     * `toVoskJsonArray` is `private`, so we exercise it indirectly through the public
     * `observeVoskGrammars` Flow. Robolectric is required to provide the real
     * Android `org.json.JSONObject.quote` implementation used inside `toVoskJsonArray`.
     *
     * The Flow uses `.debounce(300)` — we drive virtual time via `runTest`.
     */

    @Test
    fun observeVoskGrammars_twoPhrasesAndWakeWord_emitsExpectedJson() = runTest {
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

        val repo = VoiceCommandRepository(dao)

        repo.observeVoskGrammars().test {
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
    fun observeVoskGrammars_emptyPhraseList_emitsUnkFallbackJson() = runTest {
        val dao = mockk<VoiceCommandDao>()
        every { dao.observeSettings() } returns flowOf(
            VoiceWakePhraseSettingEntity(id = 1, wakeWord = "джарвис")
        )
        every { dao.observeEnabledPhrases() } returns flowOf(emptyList())

        val repo = VoiceCommandRepository(dao)

        repo.observeVoskGrammars().test {
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
