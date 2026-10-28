package ru.lsn03.voicemediacontroller.core.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeechLocaleTest {

    @Test
    fun tag_isPreservedAsIs() {
        assertThat(SpeechLocale("ru").tag).isEqualTo("ru")
        assertThat(SpeechLocale("en-US").tag).isEqualTo("en-US")
    }

    @Test
    fun equality_isValueBased() {
        assertThat(SpeechLocale("ru")).isEqualTo(SpeechLocale("ru"))
        assertThat(SpeechLocale("ru")).isNotEqualTo(SpeechLocale("en"))
    }

    @Test
    fun companion_ruAndEn_exposeStableTags() {
        assertThat(SpeechLocale.RU.tag).isEqualTo("ru")
        assertThat(SpeechLocale.EN.tag).isEqualTo("en")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTag_isRejected() {
        SpeechLocale("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun whitespaceTag_isRejected() {
        SpeechLocale("   ")
    }
}
