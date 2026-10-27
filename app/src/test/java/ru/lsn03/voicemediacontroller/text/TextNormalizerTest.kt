package ru.lsn03.voicemediacontroller.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Characterization: PR 3/4 may update assertions when consolidating normalize/grammar builders. */
class TextNormalizerTest {

    /**
     * Pins the normalization contract shared today by three private `normalize` functions
     * in `VoiceCoordinator`, `VoiceEffectsImpl` and `VoiceCommandRepository`.
     *
     * PR 3 introduced the canonical `TextNormalizer` class and this test now exercises
     * the real production implementation.
     */
    private val normalizer = TextNormalizer()

    @Test
    fun normalize_mixedCaseInput_isLowercased() {
        assertThat(normalizer.normalize("Следующий Трек")).isEqualTo("следующий трек")
    }

    @Test
    fun normalize_leadingAndTrailingWhitespace_isTrimmed() {
        assertThat(normalizer.normalize("   следующий трек   ")).isEqualTo("следующий трек")
    }

    @Test
    fun normalize_multipleInternalWhitespace_isCollapsedToSingleSpace() {
        assertThat(normalizer.normalize("следующий     трек"))
            .isEqualTo("следующий трек")
        // tabs and newlines are also whitespace and must collapse too
        assertThat(normalizer.normalize("следующий\t\nтрек"))
            .isEqualTo("следующий трек")
    }

    @Test
    fun normalize_emptyString_staysEmpty() {
        assertThat(normalizer.normalize("")).isEmpty()
    }

    @Test
    fun normalize_whitespaceOnlyString_becomesEmpty() {
        assertThat(normalizer.normalize("   \t\n ")).isEmpty()
    }

    @Test
    fun normalize_mixedCyrillicLatinPunctuation_isPreserved() {
        // Only case and whitespace are touched; punctuation and letters stay.
        assertThat(normalizer.normalize("  Hello, Джарвис!  "))
            .isEqualTo("hello, джарвис!")
    }

    @Test
    fun normalize_uppercaseCyrillicWakeWord_isLowercased() {
        assertThat(normalizer.normalize("ДЖАРВИС")).isEqualTo("джарвис")
    }
}
