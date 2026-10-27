package ru.lsn03.voicemediacontroller.voice.repo

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.db.DbSeeder.defaultPhrases
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.db.VoicePhraseEntity
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

interface VoicePhraseRepository {
    fun observePhrases(action: VoiceAction): Flow<List<VoicePhraseEntity>>
    fun observeAllEnabled(): Flow<List<VoicePhraseEntity>>
    suspend fun addPhrase(action: VoiceAction, phrase: String): Result<Unit>
    suspend fun setPhraseEnabled(id: Long, enabled: Boolean): Result<Unit>
    suspend fun deletePhrase(id: Long): Result<Unit>
    suspend fun updatePhraseText(id: Long, newText: String): Result<Unit>
    suspend fun resetToDefaults(action: VoiceAction): Result<Unit>
    fun observeBindings(): Flow<List<CommandBinding>>
}

class VoicePhraseRepositoryImpl(
    private val dao: VoiceCommandDao,
    private val normalizer: TextNormalizer,
    private val logger: AppLogger,
) : VoicePhraseRepository {

    override fun observePhrases(action: VoiceAction): Flow<List<VoicePhraseEntity>> =
        dao.observePhrasesByAction(action.name)

    override fun observeAllEnabled(): Flow<List<VoicePhraseEntity>> =
        dao.observeEnabledPhrases()

    override suspend fun addPhrase(action: VoiceAction, phrase: String): Result<Unit> {
        val raw = phrase.trim()
        if (raw.isBlank()) return Result.failure(IllegalArgumentException("EMPTY"))

        val normalized = normalizer.normalize(raw)

        return runCatching {
            dao.insertPhrase(
                VoicePhraseEntity(
                    action = action.name,
                    phrase = raw,
                    normalized = normalized,
                    enabled = true,
                )
            )
        }.recoverCatching { e ->
            if (e is SQLiteConstraintException) throw IllegalStateException("DUPLICATE")
            throw e
        }.map { }
    }

    override suspend fun setPhraseEnabled(id: Long, enabled: Boolean): Result<Unit> {
        val action = dao.getActionByPhraseId(id)
            ?: return Result.failure(IllegalStateException("NOT_FOUND"))

        if (!enabled) {
            val cnt = dao.countEnabledByAction(action)
            if (cnt <= 1) return Result.failure(IllegalStateException("LAST_ENABLED"))
        }

        return runCatching { dao.setEnabled(id, enabled) }
    }

    override suspend fun deletePhrase(id: Long): Result<Unit> {
        val row = dao.getById(id) ?: return Result.failure(IllegalStateException("NOT_FOUND"))

        if (row.enabled) {
            val cnt = dao.countEnabledByAction(row.action)
            if (cnt <= 1) return Result.failure(IllegalStateException("LAST_ENABLED"))
        }
        return runCatching { dao.deleteById(id) }
    }

    override suspend fun updatePhraseText(id: Long, newText: String): Result<Unit> {
        val raw = newText.trim()
        if (raw.isBlank()) return Result.failure(IllegalArgumentException("EMPTY"))

        val normalized = normalizer.normalize(raw)

        return runCatching {
            dao.updateText(id, raw, normalized)
        }.recoverCatching { e ->
            if (e is SQLiteConstraintException) throw IllegalStateException("DUPLICATE")
            throw e
        }
    }

    override suspend fun resetToDefaults(action: VoiceAction): Result<Unit> = runCatching {
        dao.deleteByAction(action.name)

        val defaults: List<String> = defaultPhrases(action)

        for (p in defaults) {
            val raw = p.trim()
            if (raw.isBlank()) continue
            val n = normalizer.normalize(raw)
            try {
                dao.insertPhrase(
                    VoicePhraseEntity(
                        action = action.name,
                        phrase = raw,
                        normalized = n,
                        enabled = true,
                    )
                )
            } catch (e: SQLiteConstraintException) {
                // Global unique(normalized) collision: phrase already belongs to another action.
                logger.w(APPLICATION_NAME, "Default phrase '$n' already used by another action. Skipped.")
            }
        }
    }

    override fun observeBindings(): Flow<List<CommandBinding>> =
        dao.observeEnabledPhrases().map { rows ->
            rows.groupBy { VoiceAction.valueOf(it.action) }
                .map { (action, list) ->
                    CommandBinding(list.map { it.normalized }.distinct(), action)
                }
        }
}
