package ru.lsn03.voicemediacontroller.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.di.TtsManager
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
import ru.lsn03.voicemediacontroller.voice.VoiceCoordinator
import ru.lsn03.voicemediacontroller.voice.VoiceEffects
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

    }

    @Inject
    lateinit var voiceCoordinator: VoiceCoordinator

    @Inject
    lateinit var voiceEffects: VoiceEffects

    @Inject
    lateinit var actionExecutorProvider: ActionExecutorProvider

    @Inject
    lateinit var soundPoolProvider: SoundPoolProvider

    @Inject
    lateinit var soundPrefs: SoundPrefs


    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var audioRecorder: AudioRecorder

    @Inject
    lateinit var vosk: VoskEngine

    @Inject
    lateinit var nowPlayingGateway: NowPlayingGateway

    @Inject
    lateinit var mediaControlGateway: MediaControlGateway

    @Inject
    lateinit var audioManagerControllerProvider: AudioManagerControllerProvider

    @Inject
    lateinit var audioDucker: AudioDucker

    @Inject
    lateinit var handler: Handler


    private var happyVol = 0.6f
    private var sadVol = 0.6f

    private val KEY_HAPPY_VOL = "happy_vol"
    private val KEY_SAD_VOL = "sad_vol"


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.extras?.let { b ->
            if (b.containsKey(KEY_HAPPY_VOL)) {
                soundPrefs.setHappyVol(b.getFloat(KEY_HAPPY_VOL))
                Log.d(APPLICATION_NAME, "VoiceService::onstartCommand happyVol=$happyVol")
            }
            if (b.containsKey(KEY_SAD_VOL)) {
                soundPrefs.setSadVol(b.getFloat(KEY_SAD_VOL))
                Log.d(APPLICATION_NAME, "VoiceService::onstartCommand sadVol=$sadVol")
            }
        }


        when (intent?.action) {
            ACTION_PREVIEW_WAKE -> {
                Log.d(APPLICATION_NAME, "Preview WAKE")
                soundPoolProvider.playHappy()
            }

            ACTION_PREVIEW_SLEEP -> {
                Log.d(APPLICATION_NAME, "Preview SLEEP")
                soundPoolProvider.playSad()
            }

            ACTION_OPEN_TTS_INSTALL -> {
                ttsManager.openTtsInstall()
            }

            ACTION_OPEN_TTS_SETTINGS -> {
                ttsManager.openTtsSettings()
            }
        }


        val notification = createNotification()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_STICKY
    }

    override fun onCreate() {
        Log.i(APPLICATION_NAME, "VoiceService onCreate()")
        super.onCreate()

        soundPrefs.init()
        soundPoolProvider.init()

        ttsManager.init(
            onStart = { audioDucker.start() },
            onStop = { audioDucker.stop() }
        )

        initializeVoskModel()

        startListening()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        val restartIntent = Intent(applicationContext, VoiceService::class.java)
        val pending = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        alarm.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pending)
    }

    override fun onDestroy() {
        super.onDestroy()

        audioDucker.stop()

        audioRecorder.stop()

        soundPoolProvider.release()

        handler.removeCallbacksAndMessages(null)

        ttsManager.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(APPLICATION_NAME, "No RECORD_AUDIO permission")
            publishRecognizedText("Нет разрешения на микрофон")
            return
        }

        audioRecorder.start(
            onPcm = voiceCoordinator::onPcm,
            onError = { msg ->
                Log.e(APPLICATION_NAME, msg)
                publishRecognizedText(msg)
            }
        )
    }
    
    private fun publishRecognizedText(text: String) {
        val intent = Intent(VoiceEvents.ACTION_RECOGNIZED_TEXT).apply {
            putExtra(VoiceEvents.EXTRA_TEXT, text)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, VOICE_CHANNEL)
            .setContentTitle("🎤 Слушает Джарвис")
            .setContentText("Говори 'Джарвис, следующий трек'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }


    private fun initializeVoskModel() {
        vosk.start()
    }

}

