package ru.lsn03.voicemediacontroller.audio.soundpool

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import ru.lsn03.voicemediacontroller.R
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME

class SoundPoolProvider (
    private val ctx: Context,
    private val prefs: SoundPrefs
){

    private var soundPool: SoundPool? = null
    private var sndHappy = 0
    private var sndSad = 0

    fun init(){
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()


        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            Log.d(APPLICATION_NAME, "SoundPool onLoadComplete sampleId=$sampleId status=$status")
        }
        sndHappy = soundPool!!.load(ctx, R.raw.start_water, 1)
        sndSad = soundPool!!.load(ctx, R.raw.end_water, 1)

        Log.d(APPLICATION_NAME, "SoundPool load ids: happy=$sndHappy sad=$sndSad")
    }

    fun playHappy() {
        val happyVol = prefs.getHappyVol()
        val id = soundPool?.play(sndHappy, happyVol, happyVol, 1, 0, 1f) ?: 0
        Log.d(APPLICATION_NAME, "VoiceService::playHappy soundId=$sndHappy streamId=$id vol=$happyVol")
    }

    fun playSad() {
        val sadVol = prefs.getSadVol()
        val id = soundPool?.play(sndSad, sadVol, sadVol, 1, 0, 1f) ?: 0
        Log.d(APPLICATION_NAME, "VoiceService::playSad soundId=$sndSad streamId=$id vol=$sadVol")
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}