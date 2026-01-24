package ru.lsn03.voicemediacontroller.audio.soundpool

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context
) {

    private val PREFS = "jarvis_prefs"
    private val KEY_HAPPY_VOL = "happy_vol"
    private val KEY_SAD_VOL = "sad_vol"

    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init() {
        setHappyVol(getHappyVol())
        setSadVol(getSadVol())
    }

    fun getHappyVol(): Float = prefs.getFloat(KEY_HAPPY_VOL, 0.6f).coerceIn(0f, 1f)
    fun getSadVol(): Float = prefs.getFloat(KEY_SAD_VOL, 0.6f).coerceIn(0f, 1f)

    fun setHappyVol(v: Float) = prefs.edit { putFloat(KEY_HAPPY_VOL, v.coerceIn(0f, 1f)) }
    fun setSadVol(v: Float) = prefs.edit { putFloat(KEY_SAD_VOL, v.coerceIn(0f, 1f)) }

}
