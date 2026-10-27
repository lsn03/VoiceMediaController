package ru.lsn03.voicemediacontroller.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_wake_phrase_settings")
data class VoiceWakePhraseSettingEntity(
    @PrimaryKey val id: Int = 1,
    // Must match WakeWordRepository.DEFAULT_WAKE_WORD; synced manually (data-class
    // default must be a compile-time constant, cannot reference the repo's const val).
    val wakeWord: String = "джарвис"
)
