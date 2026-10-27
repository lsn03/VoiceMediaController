package ru.lsn03.voicemediacontroller.voice.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import ru.lsn03.voicemediacontroller.db.AppDatabase
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.text.TextNormalizer

/**
 * Robolectric-backed suite exercising [VoicePhraseRepositoryImpl] against a real
 * in-memory Room database. The repo translates [android.database.sqlite.SQLiteConstraintException]
 * into `IllegalStateException("DUPLICATE")`, which only fires when a real SQLite
 * engine is driving the DAO — hence Robolectric rather than a mocked DAO.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class VoicePhraseRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: VoiceCommandDao
    private lateinit var repo: VoicePhraseRepositoryImpl
    private lateinit var logger: RecordingLogger
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
        logger = RecordingLogger()
        repo = VoicePhraseRepositoryImpl(dao, normalizer, logger)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addPhrase_happyPath_insertsAndObserveEmitsRow() = runTest {
        val result = repo.addPhrase(VoiceAction.NEXT, "следующий трек")

        assertThat(result.isSuccess).isTrue()
        val rows = repo.observePhrases(VoiceAction.NEXT).first()
        assertThat(rows).hasSize(1)
        assertThat(rows.single().phrase).isEqualTo("следующий трек")
        assertThat(rows.single().normalized).isEqualTo("следующий трек")
        assertThat(rows.single().action).isEqualTo(VoiceAction.NEXT.name)
        assertThat(rows.single().enabled).isTrue()
    }

    @Test
    fun addPhrase_blankInput_failsWithEmpty() = runTest {
        val result = repo.addPhrase(VoiceAction.NEXT, "   ")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("EMPTY")
    }

    @Test
    fun addPhrase_duplicateNormalized_mapsConstraintViolationToDuplicate() = runTest {
        val first = repo.addPhrase(VoiceAction.NEXT, "следующий трек")
        assertThat(first.isSuccess).isTrue()

        // Same normalized form (lower-cased, trimmed) across any action collides because the
        // UNIQUE index on `normalized` is table-wide.
        val second = repo.addPhrase(VoiceAction.PREV, "  Следующий Трек  ")

        assertThat(second.isFailure).isTrue()
        val cause = second.exceptionOrNull()
        assertThat(cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(cause?.message).isEqualTo("DUPLICATE")
    }

    @Test
    fun setPhraseEnabled_lastEnabledForAction_isRejected() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        val row = repo.observePhrases(VoiceAction.NEXT).first().single()

        val result = repo.setPhraseEnabled(row.id, enabled = false)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("LAST_ENABLED")

        // Row remains enabled.
        val after = dao.getById(row.id)
        assertThat(after?.enabled).isTrue()
    }

    @Test
    fun setPhraseEnabled_oneOfTwoEnabled_succeeds() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        repo.addPhrase(VoiceAction.NEXT, "некст").getOrThrow()
        val rows = repo.observePhrases(VoiceAction.NEXT).first()
        val target = rows.first { it.normalized == "некст" }

        val result = repo.setPhraseEnabled(target.id, enabled = false)

        assertThat(result.isSuccess).isTrue()
        val after = dao.getById(target.id)
        assertThat(after?.enabled).isFalse()
    }

    @Test
    fun deletePhrase_lastEnabledForAction_isRejected() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        val row = repo.observePhrases(VoiceAction.NEXT).first().single()

        val result = repo.deletePhrase(row.id)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("LAST_ENABLED")

        assertThat(dao.getById(row.id)).isNotNull()
    }

    @Test
    fun deletePhrase_notFound_failsWithNotFound() = runTest {
        val result = repo.deletePhrase(id = 9_999L)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("NOT_FOUND")
    }

    @Test
    fun deletePhrase_disabledRow_passesEvenIfOnlyRowForAction() = runTest {
        // Insert, add a sibling so we can disable the first, then delete the disabled one.
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        repo.addPhrase(VoiceAction.NEXT, "некст").getOrThrow()
        val rows = repo.observePhrases(VoiceAction.NEXT).first()
        val toDisable = rows.first { it.normalized == "некст" }
        repo.setPhraseEnabled(toDisable.id, enabled = false).getOrThrow()

        val result = repo.deletePhrase(toDisable.id)

        assertThat(result.isSuccess).isTrue()
        assertThat(dao.getById(toDisable.id)).isNull()
    }

    @Test
    fun updatePhraseText_blank_failsWithEmpty() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        val row = repo.observePhrases(VoiceAction.NEXT).first().single()

        val result = repo.updatePhraseText(row.id, "   ")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("EMPTY")
    }

    @Test
    fun updatePhraseText_newValue_persistsBothRawAndNormalized() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        val row = repo.observePhrases(VoiceAction.NEXT).first().single()

        val result = repo.updatePhraseText(row.id, "  Следующий Трек  ")

        assertThat(result.isSuccess).isTrue()
        val updated = dao.getById(row.id)!!
        assertThat(updated.phrase).isEqualTo("Следующий Трек")
        assertThat(updated.normalized).isEqualTo("следующий трек")
    }

    @Test
    fun updatePhraseText_normalizedCollision_mapsToDuplicate() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        repo.addPhrase(VoiceAction.NEXT, "некст").getOrThrow()
        val rows = repo.observePhrases(VoiceAction.NEXT).first()
        val target = rows.first { it.normalized == "некст" }

        val result = repo.updatePhraseText(target.id, "СЛЕДУЮЩИЙ")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("DUPLICATE")
    }

    @Test
    fun resetToDefaults_reseedsAction_fromDbSeederDefaults() = runTest {
        // Pre-populate with a custom phrase that will be wiped by reset.
        repo.addPhrase(VoiceAction.STOP, "finish").getOrThrow()

        val result = repo.resetToDefaults(VoiceAction.STOP)

        assertThat(result.isSuccess).isTrue()
        val rows = repo.observePhrases(VoiceAction.STOP).first()
        // DbSeeder.defaultPhrases(STOP) == listOf("стоп")
        assertThat(rows.map { it.normalized }).containsExactly("стоп")
    }

    @Test
    fun resetToDefaults_collidingDefault_isLoggedAndOtherDefaultsStillLand() = runTest {
        // Occupy "стоп" normalized form under a different action; the reset of STOP
        // will try to insert "стоп", collide on the UNIQUE(normalized) index, and the
        // repo logs a warning rather than failing the whole operation.
        repo.addPhrase(VoiceAction.NEXT, "стоп").getOrThrow()

        val result = repo.resetToDefaults(VoiceAction.STOP)

        assertThat(result.isSuccess).isTrue()
        val stopRows = repo.observePhrases(VoiceAction.STOP).first()
        assertThat(stopRows).isEmpty() // STOP table is empty, "стоп" belongs to NEXT.
        assertThat(logger.entries.map { it.level }).contains('w')
        assertThat(logger.entries.single { it.level == 'w' }.message)
            .contains("стоп")
    }

    @Test
    fun observeAllEnabled_filtersDisabledRows() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        repo.addPhrase(VoiceAction.NEXT, "некст").getOrThrow()
        repo.addPhrase(VoiceAction.STOP, "стоп").getOrThrow()
        val next = repo.observePhrases(VoiceAction.NEXT).first().first { it.normalized == "некст" }
        repo.setPhraseEnabled(next.id, enabled = false).getOrThrow()

        val enabled = repo.observeAllEnabled().first()

        assertThat(enabled.map { it.normalized })
            .containsExactly("следующий", "стоп")
    }

    @Test
    fun observePhrases_emitsAfterEveryMutation() = runTest {
        repo.observePhrases(VoiceAction.NEXT).test {
            // Initial empty snapshot.
            assertThat(awaitItem()).isEmpty()

            repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
            val afterAdd = awaitItem()
            assertThat(afterAdd.map { it.normalized }).containsExactly("следующий")

            repo.addPhrase(VoiceAction.NEXT, "некст").getOrThrow()
            val afterSecond = awaitItem()
            assertThat(afterSecond.map { it.normalized })
                .containsExactly("следующий", "некст")

            val target = afterSecond.first { it.normalized == "некст" }
            repo.setPhraseEnabled(target.id, enabled = false).getOrThrow()
            val afterToggle = awaitItem()
            assertThat(afterToggle.first { it.normalized == "некст" }.enabled).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBindings_groupsEnabledPhrasesByAction() = runTest {
        repo.addPhrase(VoiceAction.NEXT, "следующий").getOrThrow()
        repo.addPhrase(VoiceAction.NEXT, "некст").getOrThrow()
        repo.addPhrase(VoiceAction.STOP, "стоп").getOrThrow()

        val bindings = repo.observeBindings().first()

        val byAction = bindings.associateBy { it.action }
        assertThat(byAction.keys).containsExactly(VoiceAction.NEXT, VoiceAction.STOP)
        assertThat(byAction[VoiceAction.NEXT]).isEqualTo(
            CommandBinding(listOf("следующий", "некст"), VoiceAction.NEXT)
        )
        assertThat(byAction[VoiceAction.STOP]).isEqualTo(
            CommandBinding(listOf("стоп"), VoiceAction.STOP)
        )
    }
}
