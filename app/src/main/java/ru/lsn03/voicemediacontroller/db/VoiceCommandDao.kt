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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhrase(p: VoicePhraseEntity): Long

    @Query("UPDATE voice_phrase SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM voice_phrase WHERE id = :id")
    suspend fun deletePhrase(id: Long)

    @Query("SELECT * FROM voice_wake_phrase_settings WHERE id = 1")
    fun observeSettings(): Flow<VoiceWakePhraseSettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(s: VoiceWakePhraseSettingEntity)

}