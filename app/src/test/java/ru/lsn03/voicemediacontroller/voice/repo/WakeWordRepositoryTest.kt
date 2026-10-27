package ru.lsn03.voicemediacontroller.voice.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.db.AppDatabase
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.text.TextNormalizer

/**
 * Robolectric + real Room drives [WakeWordRepositoryImpl]. The repo relies on
 * `voice_wake_phrase_settings` DAO plus a `@Volatile var cached` that is written
 * both by the `observe()` collector and by `set()`. Using an actual Room instance
 * exercises the upsert path and the Flow-emission plumbing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class WakeWordRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: VoiceCommandDao
    private lateinit var repo: WakeWordRepositoryImpl
    private val normalizer = TextNormalizer()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = db.voiceCommandDao()
        repo = WakeWordRepositoryImpl(dao, normalizer)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observe_emptyDb_emitsDefaultWakeWord() = runTest {
        val emitted = repo.observe().first()

        assertThat(emitted).isEqualTo(WakeWordRepository.DEFAULT_WAKE_WORD)
    }

    @Test
    fun set_withNonBlankValue_persistsLowercasedAndTrimmed() = runTest {
        repo.set("  Алиса  ")

        val emitted = repo.observe().first()
        assertThat(emitted).isEqualTo("алиса")
    }

    @Test
    fun set_withBlankValue_fallsBackToDefault() = runTest {
        // Seed with a non-default first so we can detect fallback.
        repo.set("алиса")

        repo.set("   ")

        val emitted = repo.observe().first()
        assertThat(emitted).isEqualTo(WakeWordRepository.DEFAULT_WAKE_WORD)
    }

    @Test
    fun observe_emitsOnEverySetWrite() = runTest {
        repo.observe().test {
            // Initial emission from the empty row (settings table has no row yet) =>
            // normalizer sees "" => falls back to default.
            assertThat(awaitItem()).isEqualTo(WakeWordRepository.DEFAULT_WAKE_WORD)

            repo.set("алиса")
            assertThat(awaitItem()).isEqualTo("алиса")

            repo.set("окей гугл")
            assertThat(awaitItem()).isEqualTo("окей гугл")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun current_reflectsLastSetImmediately_beforeCollectorRuns() = runTest {
        // No collection has started yet — set() writes @Volatile cached directly, so
        // current() must observe it without waiting for the DB round-trip.
        repo.set("алиса")

        assertThat(repo.current()).isEqualTo("алиса")
    }

    @Test
    fun current_isUpdatedByObserveCollector() = runTest {
        // Pre-seed DB with a non-default value then start collecting observe() to
        // trigger the onEach { cached = it } writer path, independent of set().
        dao.upsertSettings(
            ru.lsn03.voicemediacontroller.db.VoiceWakePhraseSettingEntity(wakeWord = "алиса")
        )
        // Spin the Flow once; first() cancels after one emission which is enough to
        // run the onEach side-effect.
        val collected = repo.observe().first()
        assertThat(collected).isEqualTo("алиса")
        assertThat(repo.current()).isEqualTo("алиса")
    }

    @Test
    fun current_defaultsBeforeAnyCollection() {
        // No observe(), no set() → cached must be the default constant.
        assertThat(repo.current()).isEqualTo(WakeWordRepository.DEFAULT_WAKE_WORD)
    }
}
