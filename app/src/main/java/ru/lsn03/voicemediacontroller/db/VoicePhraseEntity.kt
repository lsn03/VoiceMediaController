package ru.lsn03.voicemediacontroller.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_phrase",
    indices = [Index(value = ["normalized"], unique = true)]
)
data class VoicePhraseEntity(

    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,     // VoiceAction.name
    val phrase: String,
    val normalized: String, // trim+lowercase
    val enabled: Boolean = true

)
