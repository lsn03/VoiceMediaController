package ru.lsn03.voicemediacontroller.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the pure-lift semantics of [WakeParser] against the branches
 * previously inlined in `VoiceCoordinator.handleWakePcm`.
 *
 * The parser trusts its caller to normalise text (trim + lowercase +
 * collapse `\s+` to single space) — these tests pass pre-normalised input
 * except where the point of the test is to document that un-normalised
 * input falls through to the `Nothing` branch.
 */
class WakeParserTest {

    private val defaultWake = "джарвис"

    private fun parserFor(wake: String): WakeParser =
        WakeParser(wakeWord = { wake })

    @Test
    fun parseFinal_exactWakeWord_returnsWakeHeardWithTitleCasedWake() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("джарвис")

        assertThat(outcome).isEqualTo(WakeOutcome.WakeHeard("Джарвис"))
    }

    @Test
    fun parseFinal_wakeFollowedByCommand_returnsWakeThenCommandWithRemainder() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("джарвис следующий трек")

        assertThat(outcome).isEqualTo(WakeOutcome.WakeThenCommand("следующий трек"))
    }

    @Test
    fun parseFinal_collapsedWhitespaceBetweenWakeAndCommand_isParsedAsWakeThenCommand() {
        // Caller is expected to normalise before calling. For a correctly
        // normalised input (single space) the parser produces the command.
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("джарвис пауза")

        assertThat(outcome).isEqualTo(WakeOutcome.WakeThenCommand("пауза"))
    }

    @Test
    fun parseFinal_internalDoubleSpaceIsNotNormalised_fallsThroughToNothing() {
        // Documents the "WakeParser trusts the caller" contract: double space
        // between the wake word and the command means the prefix "джарвис "
        // does not match on the first space, and the branch falls through.
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("джарвис  пауза")

        // "джарвис " still matches (there is a space right after), but the
        // remainder is " пауза".trim() == "пауза" — so the parser DOES emit
        // WakeThenCommand("пауза"). Pin that behaviour instead of guessing.
        assertThat(outcome).isEqualTo(WakeOutcome.WakeThenCommand("пауза"))
    }

    @Test
    fun parseFinal_wakeWithOnlyTrailingSpaces_returnsNothingNotEmptyCommand() {
        // Coder tightened behaviour vs original VoiceCoordinator: an empty
        // trimmed remainder MUST return Nothing (never WakeThenCommand("")).
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("джарвис   ")

        assertThat(outcome).isEqualTo(WakeOutcome.Nothing)
    }

    @Test
    fun parseFinal_unknownText_returnsNothing() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("какая-то фраза")

        assertThat(outcome).isEqualTo(WakeOutcome.Nothing)
    }

    @Test
    fun parseFinal_blankInput_returnsNothing() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("")

        assertThat(outcome).isEqualTo(WakeOutcome.Nothing)
    }

    @Test
    fun parseFinal_wakeAsSubstringNotPrefix_returnsNothing() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parseFinal("эй джарвис играй")

        assertThat(outcome).isEqualTo(WakeOutcome.Nothing)
    }

    @Test
    fun parseFinal_latinWakeWord_titleCasesFirstChar() {
        val parser = parserFor("jarvis")

        val outcome = parser.parseFinal("jarvis")

        assertThat(outcome).isEqualTo(WakeOutcome.WakeHeard("Jarvis"))
    }

    @Test
    fun parseFinal_wakeWordIsReadFreshOnEachCall() {
        // Lambda source may change between invocations (e.g. settings
        // flip the configured wake word). Each parseFinal call must see
        // the current value, not a cached one.
        var current = "джарвис"
        val parser = WakeParser(wakeWord = { current })

        val first = parser.parseFinal("джарвис")
        current = "алиса"
        val second = parser.parseFinal("алиса стоп")
        val third = parser.parseFinal("джарвис")

        assertThat(first).isEqualTo(WakeOutcome.WakeHeard("Джарвис"))
        assertThat(second).isEqualTo(WakeOutcome.WakeThenCommand("стоп"))
        assertThat(third).isEqualTo(WakeOutcome.Nothing)
    }

    @Test
    fun parsePartial_nonEmptyTextContainingWake_returnsHeardPartialWithTrimmedText() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parsePartial("джарвис")

        assertThat(outcome).isEqualTo(WakeOutcome.HeardPartial("джарвис"))
    }

    @Test
    fun parsePartial_nonEmptyTextWithoutWake_stillReturnsHeardPartial() {
        // parsePartial does NOT inspect wake word — any non-blank partial is
        // forwarded as HeardPartial so the UI can show "I hear: ...".
        val parser = parserFor(defaultWake)

        val outcome = parser.parsePartial("следующий")

        assertThat(outcome).isEqualTo(WakeOutcome.HeardPartial("следующий"))
    }

    @Test
    fun parsePartial_blankInput_returnsNothing() {
        val parser = parserFor(defaultWake)

        assertThat(parser.parsePartial("")).isEqualTo(WakeOutcome.Nothing)
        assertThat(parser.parsePartial("   ")).isEqualTo(WakeOutcome.Nothing)
        assertThat(parser.parsePartial("\n\t")).isEqualTo(WakeOutcome.Nothing)
    }

    @Test
    fun parsePartial_textWithSurroundingWhitespace_isTrimmedBeforeEmit() {
        val parser = parserFor(defaultWake)

        val outcome = parser.parsePartial("  джарвис  ")

        assertThat(outcome).isEqualTo(WakeOutcome.HeardPartial("джарвис"))
    }
}
