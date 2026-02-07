package ru.lsn03.voicemediacontroller.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceCommandDao {

    @Query("SELECT * FROM voice_phrase WHERE enabled = 1")
    fun observeEnabledPhrases(): Flow<List<VoicePhraseEntity>>

    @Query("SELECT * FROM voice_phrase")
    fun observeAllPhrases(): Flow<List<VoicePhraseEntity>>

    @Query("UPDATE voice_phrase SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM voice_phrase WHERE id = :id")
    suspend fun deletePhrase(id: Long)

    @Query("SELECT * FROM voice_wake_phrase_settings WHERE id = 1")
    fun observeSettings(): Flow<VoiceWakePhraseSettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(s: VoiceWakePhraseSettingEntity)

    @Query("SELECT * FROM voice_phrase WHERE [action] = :action ORDER BY id ASC")
    fun observePhrasesByAction(action: String): Flow<List<VoicePhraseEntity>>

    @Query("SELECT COUNT(*) FROM voice_phrase WHERE [action] = :action AND enabled = 1")
    suspend fun countEnabledByAction(action: String): Int

    @Query("SELECT [action] FROM voice_phrase WHERE id = :id LIMIT 1")
    suspend fun getActionByPhraseId(id: Long): String?

    @Query("DELETE FROM voice_phrase WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE voice_phrase SET phrase = :phrase, normalized = :normalized WHERE id = :id")
    suspend fun updateText(id: Long, phrase: String, normalized: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhrase(entity: VoicePhraseEntity): Long

    @Query("DELETE FROM voice_phrase WHERE [action] = :action")
    suspend fun deleteByAction(action: String)

    @Query("SELECT * FROM voice_phrase WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VoicePhraseEntity?

}