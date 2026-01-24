package ru.lsn03.voicemediacontroller.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VoicePhraseEntity::class, VoiceWakePhraseSettingEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceCommandDao(): VoiceCommandDao
}