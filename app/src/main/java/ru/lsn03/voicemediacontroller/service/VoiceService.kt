package ru.lsn03.voicemediacontroller.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.di.TtsManager
import ru.lsn03.voicemediacontroller.service.notification.VoiceNotificationController
import ru.lsn03.voicemediacontroller.service.pipeline.VoicePipeline
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatus
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatusBus
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import javax.inject.Inject

@AndroidEntryPoint
class VoiceService : Service() {
    companion object {
        val SAMPLE_RATE = 16000
        const val ACTION_PREVIEW_WAKE = "ru.lsn03.voicemediacontroller.action.PREVIEW_WAKE"
        const val ACTION_PREVIEW_SLEEP = "ru.lsn03.voicemediacontroller.action.PREVIEW_SLEEP"
        const val ACTION_OPEN_TTS_INSTALL = "ru.lsn03.voicemediacontroller.action.OPEN_TTS_INSTALL"
        const val ACTION_OPEN_TTS_SETTINGS = "ru.lsn03.voicemediacontroller.action.OPEN_TTS_SETTINGS"
        const val NOTIF_TTS_HELP_ID = 2
        private const val KEY_HAPPY_VOL = "happy_vol"
        private const val KEY_SAD_VOL = "sad_vol"
    }

    @Inject lateinit var wakeRepo: WakeWordRepository
    @Inject lateinit var pipeline: VoicePipeline
    @Inject lateinit var notificationController: VoiceNotificationController
    @Inject lateinit var soundPoolProvider: SoundPoolProvider
    @Inject lateinit var soundPrefs: SoundPrefs
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var vosk: VoskEngine
    @Inject lateinit var audioDucker: AudioDucker
    @Inject lateinit var handler: Handler
    @Inject lateinit var recognizerStatusBus: RecognizerStatusBus

    // Written by wakeRepo collector; read from onStartCommand on another thread.
    @Volatile private var currentWake: String = WakeWordRepository.DEFAULT_WAKE_WORD
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        Log.i(APPLICATION_NAME, "VoiceService onCreate()")
        super.onCreate()
        soundPrefs.init()
        soundPoolProvider.init()
        ttsManager.init(onStart = { audioDucker.start() }, onStop = { audioDucker.stop() })
        vosk.start()
        notificationController.ensureChannel()
        serviceScope.launch {
            wakeRepo.observe().collect { wake ->
                currentWake = wake
                notificationController.refresh(wake)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(APPLICATION_NAME, "No RECORD_AUDIO permission")
            recognizerStatusBus.post(RecognizerStatus.Error("Нет разрешения на микрофон"))
            return
        }
        pipeline.start(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.extras?.let { b ->
            if (b.containsKey(KEY_HAPPY_VOL)) soundPrefs.setHappyVol(b.getFloat(KEY_HAPPY_VOL))
            if (b.containsKey(KEY_SAD_VOL)) soundPrefs.setSadVol(b.getFloat(KEY_SAD_VOL))
        }
        when (intent?.action) {
            ACTION_PREVIEW_WAKE -> soundPoolProvider.playHappy()
            ACTION_PREVIEW_SLEEP -> soundPoolProvider.playSad()
            ACTION_OPEN_TTS_INSTALL -> ttsManager.openTtsInstall()
            ACTION_OPEN_TTS_SETTINGS -> ttsManager.openTtsSettings()
        }
        val notif = notificationController.build(currentWake)
        startForeground(VoiceNotificationController.NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restart = Intent(applicationContext, VoiceService::class.java)
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getService(this, 1, restart, flags)
        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        alarm.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pending)
    }

    override fun onDestroy() {
        super.onDestroy()
        pipeline.stop()
        serviceScope.cancel()
        audioDucker.stop()
        soundPoolProvider.release()
        handler.removeCallbacksAndMessages(null)
        ttsManager.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = TODO("Not yet implemented")
}
