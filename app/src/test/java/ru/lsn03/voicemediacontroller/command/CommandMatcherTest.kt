package ru.lsn03.voicemediacontroller.command

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.lsn03.voicemediacontroller.action.VoiceAction

/** Characterization: PR 3/4 may update assertions when consolidating normalize/grammar builders. */
class CommandMatcherTest {

    /**
     * Current matcher semantics (pinned here so later PRs cannot change them silently):
     *
     *   fun match(text: String): VoiceAction? =
     *       bindings.firstOrNull { text in it.phrases }?.action
     *
     * i.e. exact (case-sensitive) membership against the first `CommandBinding`
     * that contains `text`. Normalization is caller's responsibility; the matcher
     * never trims nor lowercases on its own. These tests encode that contract.
     */
    private val bindings: List<CommandBinding> = listOf(
        CommandBinding(
            phrases = listOf("следующий трек", "следующая"),
            action = VoiceAction.NEXT
        ),
        CommandBinding(
            phrases = listOf("предыдущий трек", "предыдущая"),
            action = VoiceAction.PREV
        )
    )

    @Test
    fun match_exactPhraseInFirstBinding_returnsItsAction() {
        val matcher = CommandMatcher(bindings)

        assertThat(matcher.match("следующий трек")).isEqualTo(VoiceAction.NEXT)
    }

    @Test
    fun match_exactPhraseInSecondBinding_returnsItsAction() {
        val matcher = CommandMatcher(bindings)

        assertThat(matcher.match("предыдущая")).isEqualTo(VoiceAction.PREV)
    }

    @Test
    fun match_unknownPhrase_returnsNull() {
        val matcher = CommandMatcher(bindings)

        assertThat(matcher.match("пауза")).isNull()
    }

    @Test
    fun match_phraseWithWrongCase_returnsNull() {
        // Matcher is case-sensitive today — fixture holds lowercase, so
        // an uppercase input must miss. Callers are expected to normalise first.
        val matcher = CommandMatcher(bindings)

        assertThat(matcher.match("Следующий Трек")).isNull()
    }

    @Test
    fun match_emptyBindingList_alwaysReturnsNull() {
        val matcher = CommandMatcher(emptyList())

        assertThat(matcher.match("следующий трек")).isNull()
        assertThat(matcher.match("")).isNull()
    }
}
