package ru.lsn03.voicemediacontroller.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_wake_phrase_settings")
data class VoiceWakePhraseSettingEntity(
    @PrimaryKey val id: Int = 1,
    val wakeWord: String = "джарвис"
)
