package ru.lsn03.voicemediacontroller.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
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
import kotlinx.coroutines.*
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.di.TtsManager
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
import ru.lsn03.voicemediacontroller.voice.VoiceCommandRepository
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

        private const val NOTIF_ID = 1
    }

    @Inject
    lateinit var repo: VoiceCommandRepository

    @Inject
    lateinit var voiceCoordinator: VoiceCoordinator

    @Inject
    lateinit var voiceEffects: VoiceEffects

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
    lateinit var audioDucker: AudioDucker

    @Inject
    lateinit var handler: Handler


    private var happyVol = 0.6f
    private var sadVol = 0.6f

    private val KEY_HAPPY_VOL = "happy_vol"
    private val KEY_SAD_VOL = "sad_vol"


    @Volatile private var currentWake: String = "Джарвис"


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


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


        val notification = createNotification(currentWake)
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
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

        voiceEffects.start(serviceScope)

        // ВАЖНО: collect грамматик отдельно (тоже один раз)
        Log.d(APPLICATION_NAME, "Subscribing to grammars")
        serviceScope.launch {
            repo.observeWakeWord()
                .collect { wake ->
                    Log.d(APPLICATION_NAME, "Wake word updated: $wake")
                    currentWake = wake
                    voiceCoordinator.setWakeWord(wake)
                    updateForegroundNotification(wake)
                }
        }

        serviceScope.launch {
            // лучше использовать repo.grammars(serviceScope), раз ты его уже сделал
            repo.grammars(this).collect { g ->
                Log.d(APPLICATION_NAME, "Applying grammars: wake=${g.wakeWordGrammarJson.length}, cmd=${g.commandGrammarJson.length}")
                vosk.updateGrammars(g.wakeWordGrammarJson, g.commandGrammarJson, g.wakeCommandGrammarJson)
            }
        }


        startListening()
    }

    private fun updateForegroundNotification(wake: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, createNotification(wake))
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
        serviceScope.cancel()

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

    private fun createNotification(wake: String): Notification {
        val w = wake.trim().ifBlank { "Джарвис" }
        val titleWake = w.replaceFirstChar { it.uppercaseChar() }

        return NotificationCompat.Builder(this, VOICE_CHANNEL)
            .setContentTitle("🎤 Слушает $titleWake")
            .setContentText("Говори '$titleWake, следующий трек'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // важно, чтобы не дёргало уведомлением при обновлении
            .build()
    }


    private fun initializeVoskModel() {
        vosk.start()
    }

}

