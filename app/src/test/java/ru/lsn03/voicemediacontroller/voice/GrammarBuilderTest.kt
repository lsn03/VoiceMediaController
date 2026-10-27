package ru.lsn03.voicemediacontroller.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository

/**
 * Direct, deterministic tests for [GrammarBuilder]. The builder calls
 * `org.json.JSONObject.quote`, which on pure JVM is an Android-stub that returns
 * `null` — therefore the suite runs under Robolectric to get the real Android
 * `org.json` implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class GrammarBuilderTest {

    private lateinit var logger: RecordingLogger
    private lateinit var builder: GrammarBuilder

    @Before
    fun setUp() {
        logger = RecordingLogger()
        builder = GrammarBuilder(TextNormalizer(), logger)
    }

    @Test
    fun build_emptyPhraseList_commandAndWakeCommandFallBackToUnk() {
        val g = builder.build(wakeWord = "джарвис", phrases = emptyList())

        assertThat(g.wakeWordGrammarJson).isEqualTo("[\"джарвис\",\"[unk]\"]")
        assertThat(g.commandGrammarJson).isEqualTo("[\"[unk]\"]")
        // commands is empty => wakeCommandJson == distinct("[unk]") => just the fallback.
        assertThat(g.wakeCommandGrammarJson).isEqualTo("[\"[unk]\"]")
        assertThat(logger.entries).isEmpty()
    }

    @Test
    fun build_blankWakeWord_defaultsToWakeWordRepositoryConstant() {
        val g = builder.build(wakeWord = "   ", phrases = listOf("стоп"))

        val defaultWake = WakeWordRepository.DEFAULT_WAKE_WORD
        assertThat(g.wakeWordGrammarJson)
            .isEqualTo("[\"$defaultWake\",\"[unk]\"]")
        assertThat(g.commandGrammarJson).isEqualTo("[\"стоп\"]")
        assertThat(g.wakeCommandGrammarJson)
            .isEqualTo("[\"$defaultWake стоп\",\"[unk]\"]")
    }

    @Test
    fun build_singlePhrase_emitsExpectedJson() {
        val g = builder.build(wakeWord = "джарвис", phrases = listOf("стоп"))

        assertThat(g.wakeWordGrammarJson).isEqualTo("[\"джарвис\",\"[unk]\"]")
        assertThat(g.commandGrammarJson).isEqualTo("[\"стоп\"]")
        assertThat(g.wakeCommandGrammarJson)
            .isEqualTo("[\"джарвис стоп\",\"[unk]\"]")
    }

    @Test
    fun build_duplicatePhrases_areStrippedAfterNormalization() {
        val g = builder.build(
            wakeWord = "джарвис",
            phrases = listOf("Стоп", "стоп", "  стоп  ")
        )

        assertThat(g.commandGrammarJson).isEqualTo("[\"стоп\"]")
        assertThat(g.wakeCommandGrammarJson)
            .isEqualTo("[\"джарвис стоп\",\"[unk]\"]")
    }

    @Test
    fun build_blankPhrases_areFilteredOut() {
        val g = builder.build(
            wakeWord = "джарвис",
            phrases = listOf("", "   ", "следующий")
        )

        assertThat(g.commandGrammarJson).isEqualTo("[\"следующий\"]")
        assertThat(g.wakeCommandGrammarJson)
            .isEqualTo("[\"джарвис следующий\",\"[unk]\"]")
    }

    @Test
    fun build_twoDistinctPhrases_appearInInsertionOrder() {
        val g = builder.build(
            wakeWord = "джарвис",
            phrases = listOf("следующий трек", "предыдущий трек")
        )

        assertThat(g.commandGrammarJson)
            .isEqualTo("[\"следующий трек\",\"предыдущий трек\"]")
        assertThat(g.wakeCommandGrammarJson)
            .isEqualTo("[\"джарвис следующий трек\",\"джарвис предыдущий трек\",\"[unk]\"]")
    }

    @Test
    fun build_overflowsMaxJsonChars_fallsBackToUnkAndLogsWarning() {
        // MAX_JSON_CHARS == 50_000. Each phrase is ~1100 chars + 3 bytes of JSON overhead,
        // so 50 unique entries push well past the limit.
        val huge = (1..50).map { i ->
            buildString(1_100) {
                repeat(1_100) { append(('а' + (i % 26))) }
                // Make each unique by suffixing an index so distinct() does not collapse.
                append(" $i")
            }
        }

        val g = builder.build(wakeWord = "джарвис", phrases = huge)

        // The overflow branch returns the UNK_FALLBACK_JSON literal, bypassing the
        // closing `]` sb.append in the normal path.
        assertThat(g.commandGrammarJson).isEqualTo("[\"[unk]\"]")
        val warnings = logger.entries.filter { it.level == 'w' }
        assertThat(warnings).isNotEmpty()
        assertThat(warnings.first().message).contains("Grammar JSON too big")
    }

    @Test
    fun build_quotesJsonSpecialCharacters() {
        // Make sure `JSONObject.quote` is actually running — a quote in the phrase must
        // be escaped. The normalizer only trims/lower-cases so the double-quote stays.
        val g = builder.build(wakeWord = "джарвис", phrases = listOf("say \"hi\""))

        // Lowercased, then quoted: `\"say \\\"hi\\\"\"`.
        assertThat(g.commandGrammarJson).contains("\\\"hi\\\"")
    }
}
