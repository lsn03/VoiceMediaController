package ru.lsn03.voicemediacontroller.core.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the shape of the static [ModelCatalog]: downstream code
 * (`ModelDownloader`, `ModelsViewModel`, `AssetModelProvider`) relies on
 * `ru` being asset-only (`url == null`) and `en` carrying a real HTTP URL.
 * Breaking either invariant silently changes product behaviour, so lock
 * them down here.
 */
class ModelCatalogTest {

    @Test
    fun locales_contains_ru_and_en() {
        val locales = ModelCatalog.locales()

        assertThat(locales).containsAtLeast(SpeechLocale.RU, SpeechLocale.EN)
    }

    @Test
    fun entryFor_ru_hasNullUrl_markingItAssetOnly() {
        val entry = ModelCatalog.entryFor(SpeechLocale.RU)

        assertThat(entry).isNotNull()
        assertThat(entry!!.url).isNull()
    }

    @Test
    fun entryFor_en_hasNonNullNonBlankUrl() {
        val entry = ModelCatalog.entryFor(SpeechLocale.EN)

        assertThat(entry).isNotNull()
        assertThat(entry!!.url).isNotNull()
        assertThat(entry.url).isNotEmpty()
    }

    @Test
    fun entryFor_unknownLocale_returnsNull() {
        assertThat(ModelCatalog.entryFor(SpeechLocale("xx"))).isNull()
    }
}
