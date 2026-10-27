package ru.lsn03.voicemediacontroller.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Characterization: PR 3/4 may update assertions when consolidating normalize/grammar builders. */
class TextNormalizerTest {

    /**
     * Pins the normalization contract shared today by three private `normalize` functions
     * in `VoiceCoordinator`, `VoiceEffectsImpl` and `VoiceCommandRepository`.
     *
     * This local copy mirrors the `VoiceCommandRepository` version:
     *   trim + lowercase + collapse `\s+` -> " "
     *
     * PR 3 is scheduled to introduce a canonical `TextNormalizer` class that must satisfy
     * these assertions verbatim. When that class lands, the local `normalize` below is
     * replaced by the production implementation and these tests move with it.
     */
    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")

    @Test
    fun normalize_mixedCaseInput_isLowercased() {
        assertThat(normalize("Следующий Трек")).isEqualTo("следующий трек")
    }

    @Test
    fun normalize_leadingAndTrailingWhitespace_isTrimmed() {
        assertThat(normalize("   следующий трек   ")).isEqualTo("следующий трек")
    }

    @Test
    fun normalize_multipleInternalWhitespace_isCollapsedToSingleSpace() {
        assertThat(normalize("следующий     трек"))
            .isEqualTo("следующий трек")
        // tabs and newlines are also whitespace and must collapse too
        assertThat(normalize("следующий\t\nтрек"))
            .isEqualTo("следующий трек")
    }

    @Test
    fun normalize_emptyString_staysEmpty() {
        assertThat(normalize("")).isEmpty()
    }

    @Test
    fun normalize_whitespaceOnlyString_becomesEmpty() {
        assertThat(normalize("   \t\n ")).isEmpty()
    }

    @Test
    fun normalize_mixedCyrillicLatinPunctuation_isPreserved() {
        // Only case and whitespace are touched; punctuation and letters stay.
        assertThat(normalize("  Hello, Джарвис!  "))
            .isEqualTo("hello, джарвис!")
    }

    @Test
    fun normalize_uppercaseCyrillicWakeWord_isLowercased() {
        assertThat(normalize("ДЖАРВИС")).isEqualTo("джарвис")
    }
}
