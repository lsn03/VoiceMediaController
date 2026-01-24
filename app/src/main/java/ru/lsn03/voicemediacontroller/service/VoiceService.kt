package ru.lsn03.voicemediacontroller.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.SoundPool
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPrefs
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.command.CommandMatcher
import ru.lsn03.voicemediacontroller.di.TtsManager
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.media.MediaControlGateway
import ru.lsn03.voicemediacontroller.media.NowPlayingGateway
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
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
        const val NOTIF_TTS_HELP_ID = 2     // отдельная нотификация-помощник

    }

    private lateinit var voiceCoordinator: VoiceCoordinator

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

    private var isListeningCommand = false

    @Inject
    lateinit var handler: Handler

    private val commandTimeoutRunnable = Runnable {
        if (isListeningCommand) {
            Log.d(APPLICATION_NAME, "VoiceService::commandTimeoutRunnable Таймаут команд, назад к wake")
            resetToWakeMode()
        }
    }

    private var soundPool: SoundPool? = null
    private var happyVol = 0.6f
    private var sadVol = 0.6f

    private val KEY_HAPPY_VOL = "happy_vol"
    private val KEY_SAD_VOL = "sad_vol"

    private val matcher by lazy {
        CommandMatcher(
            listOf(
                CommandBinding(
                    listOf("следующий трек", "следующий", "некст", "че за хуйня", "что за хуйня"),
                    VoiceAction.NEXT
                ),
                CommandBinding(listOf("предыдущий трек", "предыдущий", "прев"), VoiceAction.PREV),
                CommandBinding(listOf("пауза", "стоп"), VoiceAction.STOP),
                CommandBinding(
                    listOf("продолжи", "продолжить", "возобнови", "плей", "плэй", "играй", "старт"),
                    VoiceAction.START
                ),
                CommandBinding(listOf("тише", "уменьши"), VoiceAction.VOLUME_DOWN),
                CommandBinding(listOf("громче", "увеличь"), VoiceAction.VOLUME_UP),
                CommandBinding(listOf("время"), VoiceAction.SAY_TIME),
                CommandBinding(listOf("название"), VoiceAction.SAY_TITLE),
            )
        )
    }

    private val COMMAND_TIMEOUT_MS = 10000L  // 10 секунд

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
                ttsManager. openTtsSettings()
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
        // пользователь смахнул приложение из recent apps
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

        handler.removeCallbacks(commandTimeoutRunnable)
        audioDucker.stop() // <-- на всякий случай

        audioRecorder.stop()

        soundPool?.release()
        soundPool = null

        handler.removeCallbacksAndMessages(null) // опционально, если хочешь «обнулить очередь»

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


    private fun switchToCommandModeInternal() {
        audioDucker.start() // <-- ДО начала распознавания команды
        isListeningCommand = true

        vosk.resetCommand()

        publishRecognizedText("Слушаю команду...")
        handler.removeCallbacks(commandTimeoutRunnable)
        handler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)
        Log.d(APPLICATION_NAME, "VoiceService::switchToCommandModeInternal")
    }


    private fun resetToWakeModeInternal() {
        isListeningCommand = false
        handler.removeCallbacks(commandTimeoutRunnable)

        audioDucker.stop() // <-- ВСЕГДА отпускаем фокус при выходе из команд

        vosk.resetCommand()
        vosk.resetWake()

        soundPoolProvider.playSad() //— оставь как тебе нужно (у тебя оно уже есть и тут, и в handleCommand)
        publishRecognizedText("Слушаю...")
        Log.d(APPLICATION_NAME, "VoiceService::resetToWakeModeInternal")
    }


    private fun normalize(s: String) = s.trim().lowercase()


    private fun handleCommandText(cmd: String) {
        val text = normalize(cmd)
        if (text.isEmpty()) {
            Log.d(APPLICATION_NAME, "Empty command text")
            return
        }
        Log.d(APPLICATION_NAME, "Command text: $text")
        publishRecognizedText("Выполняю: $text")

        val action = matcher.match(text) ?: VoiceAction.UNKNOWN
        Log.d(APPLICATION_NAME, "Action=$action")

        val exec = actionExecutorProvider.getExecutor(action)

        exec.execute()

        resetToWakeMode()
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
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)  // 👈 ИБАЗАТЕЛЬНО!
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }


    private fun initializeVoskModel() {
        vosk.start()

        voiceCoordinator = VoiceCoordinator(
            vosk,
            soundPoolProvider = soundPoolProvider,
            handleCommandText = ::handleCommandText,
            publishRecognizedText = ::publishRecognizedText,
            switchToCommandModeInternal = ::switchToCommandModeInternal,
            resetToWakeModeInternal = ::resetToWakeModeInternal,
        )
    }


    private fun resetToWakeMode() {
        voiceCoordinator.resetToWakeMode()
    }

}

