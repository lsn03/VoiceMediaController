package ru.lsn03.voicemediacontroller.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ru.lsn03.voicemediacontroller.R
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProvider
import ru.lsn03.voicemediacontroller.audio.AudioManagerControllerProviderImpl
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDuckerImpl
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.command.CommandMatcher
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.media.*
import ru.lsn03.voicemediacontroller.tts.SpeechGateway
import ru.lsn03.voicemediacontroller.tts.SpeechGatewayImpl
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import java.util.*


class VoiceService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var vosk: VoskEngine
    private lateinit var voiceCoordinator: VoiceCoordinator
    private lateinit var audioManagerControllerProvider: AudioManagerControllerProvider
    private lateinit var actionExecutorProvider: ActionExecutorProvider
    private lateinit var mediaControlGateway: MediaControlGateway
    private lateinit var mediaControllerProvider: MediaControllerProvider
    private lateinit var speechGateway: SpeechGateway
    private lateinit var nowPlayingGateway: NowPlayingGateway
    private lateinit var audioManager: AudioManager
    private lateinit var audioDucker: AudioDucker

    companion object {
        val SAMPLE_RATE = 16000

        const val ACTION_PREVIEW_WAKE = "ru.lsn03.voicemediacontroller.action.PREVIEW_WAKE"
        const val ACTION_PREVIEW_SLEEP = "ru.lsn03.voicemediacontroller.action.PREVIEW_SLEEP"

        const val ACTION_OPEN_TTS_INSTALL = "ru.lsn03.voicemediacontroller.action.OPEN_TTS_INSTALL"
        const val ACTION_OPEN_TTS_SETTINGS = "ru.lsn03.voicemediacontroller.action.OPEN_TTS_SETTINGS"
        const val NOTIF_TTS_HELP_ID = 2     // отдельная нотификация-помощник

    }

    private var focusReq: AudioFocusRequest? = null

    @Volatile
    private var duckActive = false

    private val afListener = AudioManager.OnAudioFocusChangeListener { /* можно игнорить */ }


    private var isListeningCommand = false


    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady: Boolean = false


    @Volatile
    private var pendingResetToWake = false


    private val handler = Handler(Looper.getMainLooper())

    private val commandTimeoutRunnable = Runnable {
        if (isListeningCommand) {
            Log.d(APPLICATION_NAME, "VoiceService::commandTimeoutRunnable Таймаут команд, назад к wake")
            resetToWakeMode()
        }
    }


    private var soundPool: SoundPool? = null
    private var sndHappy = 0
    private var sndSad = 0

    private var happyVol = 0.6f
    private var sadVol = 0.6f

    private val PREFS = "jarvis_prefs"
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
                happyVol = b.getFloat(KEY_HAPPY_VOL).coerceIn(0f, 1f)
                getSharedPreferences(PREFS, MODE_PRIVATE).edit { putFloat(KEY_HAPPY_VOL, happyVol) }
                Log.d(APPLICATION_NAME, "happyVol=$happyVol")
            }
            if (b.containsKey(KEY_SAD_VOL)) {
                sadVol = b.getFloat(KEY_SAD_VOL).coerceIn(0f, 1f)
                getSharedPreferences(PREFS, MODE_PRIVATE).edit { putFloat(KEY_SAD_VOL, sadVol) }
                Log.d(APPLICATION_NAME, "sadVol=$sadVol")
            }
        }

        when (intent?.action) {
            ACTION_PREVIEW_WAKE -> {
                Log.d(APPLICATION_NAME, "Preview WAKE")
                playHappy()
            }

            ACTION_PREVIEW_SLEEP -> {
                Log.d(APPLICATION_NAME, "Preview SLEEP")
                playSad()
            }

            ACTION_OPEN_TTS_INSTALL -> {
                openTtsInstall()
            }

            ACTION_OPEN_TTS_SETTINGS -> {
                openTtsSettings()
            }
        }


        val notification = createNotification()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_STICKY
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

    override fun onCreate() {
        Log.i(APPLICATION_NAME, "VoiceService onCreate()")
        super.onCreate()


        audioRecorder = AudioRecorder(sampleRate = SAMPLE_RATE)


        tts = initializeTts()

        initializeActionExecutorProvider()

        initializeVoskModel()

        // prefs
        initializePref()

        // SoundPool
        initializeSoundPool()

        startListening()
    }

    private fun initializeActionExecutorProvider() {
        speechGateway = SpeechGatewayImpl(
            handler = handler,
            isReady = { ttsReady },
            ttsProvider = { tts }
        )
        mediaControllerProvider = MediaControllerProviderImpl(this)
        nowPlayingGateway = NowPlayingGatewayImpl(mediaControllerProvider)
        mediaControlGateway = MediaControlGatewayImpl(mediaControllerProvider)
        audioManagerControllerProvider = AudioManagerControllerProviderImpl(this)
        actionExecutorProvider = ActionExecutorProvider(
            audioManagerControllerProvider,
            mediaControlGateway,
            speechGateway,
            nowPlayingGateway
        )

        audioManager = audioManagerControllerProvider.getAudioManager()

        audioDucker = AudioDuckerImpl(audioManager, handler)
    }

    private fun initializeSoundPool() {
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
        sndHappy = soundPool!!.load(this, R.raw.start_water, 1)
        sndSad = soundPool!!.load(this, R.raw.end_water, 1)

        Log.d(APPLICATION_NAME, "SoundPool load ids: happy=$sndHappy sad=$sndSad")
    }

    private fun initializePref() {
        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        happyVol = sp.getFloat(KEY_HAPPY_VOL, 0.6f)
        sadVol = sp.getFloat(KEY_SAD_VOL, 0.6f)
    }

    private fun initializeVoskModel() {
        vosk = VoskEngine(
            context = this,
            sampleRate = SAMPLE_RATE
        )
        vosk.start()

        voiceCoordinator = VoiceCoordinator(
            vosk,
            ::handleCommandText,
            publishRecognizedText = ::publishRecognizedText,
            playHappy = ::playHappy,
            switchToCommandModeInternal = ::switchToCommandModeInternal,
            resetToWakeModeInternal = ::resetToWakeModeInternal,
        )
    }

    private fun initializeTts(): TextToSpeech = TextToSpeech(applicationContext) { status ->
        ttsReady = (status == TextToSpeech.SUCCESS)

        Log.d(APPLICATION_NAME, "initialization TTS,status=$status")
        if (ttsReady) {
            //                tts?.language = Locale("ru", "RU") // или Locale.getDefault()
            tts?.language = Locale.getDefault()

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    // onStart приходит не на main thread
                    handler.post { audioDucker.start() }
                }

                override fun onDone(utteranceId: String) {
                    handler.post { audioDucker.stop() }
                }

                override fun onError(utteranceId: String) {
                    handler.post { audioDucker.stop() }
                }
            })

        }
        if (status != TextToSpeech.SUCCESS) {
            Log.d(
                APPLICATION_NAME,
                "В системе не настроен движок синтеза речи. Нажми «Установить» или открой «Настройки»."
            )
            showTtsFixNotification("В системе не настроен движок синтеза речи. Нажми «Установить» или открой «Настройки».")
        }

    }

    private fun openTtsInstall() {
        val i = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(i)
        } catch (e: Exception) {
            Log.e(APPLICATION_NAME, "No activity for ACTION_INSTALL_TTS_DATA", e)
            openTtsSettings()
        }
    }

    private fun openTtsSettings() {
        val i = Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(i)
        } catch (e: Exception) {
            // Фоллбэк на общие настройки
            startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun showTtsFixNotification(reason: String) {
        val installIntent = Intent(this, VoiceService::class.java).apply { action = ACTION_OPEN_TTS_INSTALL }
        val settingsIntent = Intent(this, VoiceService::class.java).apply { action = ACTION_OPEN_TTS_SETTINGS }

        val piInstall = PendingIntent.getService(
            this, 2001, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val piSettings = PendingIntent.getService(
            this, 2002, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(this, VOICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Нужен синтез речи (TTS)")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "Установить", piInstall)
            .addAction(android.R.drawable.ic_menu_preferences, "Настройки", piSettings)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_TTS_HELP_ID, n)
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

        playSad() //— оставь как тебе нужно (у тебя оно уже есть и тут, и в handleCommand)
        publishRecognizedText("Слушаю...")
        Log.d(APPLICATION_NAME, "VoiceService::resetToWakeModeInternal")
    }


    fun normalize(s: String) = s.trim().lowercase()


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

    private fun resetToWakeMode() {
        pendingResetToWake = true
    }

    private fun playHappy() {
        val id = soundPool?.play(sndHappy, happyVol, happyVol, 1, 0, 1f) ?: 0
        Log.d(APPLICATION_NAME, "VoiceService::playHappy soundId=$sndHappy streamId=$id vol=$happyVol")
    }

    private fun playSad() {
        val id = soundPool?.play(sndSad, sadVol, sadVol, 1, 0, 1f) ?: 0
        Log.d(APPLICATION_NAME, "VoiceService::playSad soundId=$sndSad streamId=$id vol=$sadVol")
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacks(commandTimeoutRunnable)
        audioDucker.stop() // <-- на всякий случай

        audioRecorder.stop()

        soundPool?.release()
        soundPool = null

        handler.removeCallbacksAndMessages(null) // опционально, если хочешь «обнулить очередь»

        handler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ttsReady = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
}

