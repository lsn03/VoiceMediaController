package ru.lsn03.voicemediacontroller.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.SoundPool
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import ru.lsn03.voicemediacontroller.R
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.command.CommandMatcher
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.MODEL_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import ru.lsn03.voicemediacontroller.vosk.VoskResult
import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*


class VoiceService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var vosk: VoskEngine


    companion object {
        val SAMPLE_RATE = 16000
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        const val ACTION_PREVIEW_WAKE = "ru.lsn03.voicemediacontroller.action.PREVIEW_WAKE"
        const val ACTION_PREVIEW_SLEEP = "ru.lsn03.voicemediacontroller.action.PREVIEW_SLEEP"

        const val ACTION_OPEN_TTS_INSTALL = "ru.lsn03.voicemediacontroller.action.OPEN_TTS_INSTALL"
        const val ACTION_OPEN_TTS_SETTINGS = "ru.lsn03.voicemediacontroller.action.OPEN_TTS_SETTINGS"
        const val NOTIF_ID = 1              // у тебя уже startForeground(1,...)
        const val NOTIF_TTS_HELP_ID = 2     // отдельная нотификация-помощник

    }

    private var focusReq: android.media.AudioFocusRequest? = null
    @Volatile
    private var duckActive = false

    private val afListener = android.media.AudioManager.OnAudioFocusChangeListener { /* можно игнорить */ }


    private var isListeningCommand = false
    private var lastUiUpdateMs = 0L
    private var lastWakeTriggerMs = 0L
    private val UI_THROTTLE_MS = 250L        // не чаще 4 раз/сек
    private val WAKE_DEBOUNCE_MS = 1200L     // защита от повторов "джарвис"

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady: Boolean = false


    @Volatile
    private var pendingResetToWake = false

    @Volatile
    private var pendingSwitchToCommand = false

    @Volatile
    private var isRunning = true

    private val handler = Handler(Looper.getMainLooper())

    private val commandTimeoutRunnable = Runnable {
        if (isListeningCommand) {
            Log.d(APPLICATION_NAME, "VoiceService::commandTimeoutRunnable Таймаут команд, назад к wake")
            resetToWakeMode()
        }
    }

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as android.media.AudioManager
    }



    private var soundPool: android.media.SoundPool? = null
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

    private val executor: ActionExecutor = object : ActionExecutor {
        override fun execute(action: VoiceAction) {
            when (action) {
                VoiceAction.NEXT -> nextTrack()
                VoiceAction.PREV -> prevTrack()
                VoiceAction.START -> playPlayback()
                VoiceAction.STOP -> pausePlayback()
                VoiceAction.VOLUME_UP -> volumeUp()
                VoiceAction.VOLUME_DOWN -> volumeDown()
                VoiceAction.SAY_TIME -> speakTime()
                VoiceAction.SAY_TITLE -> speakNowPlaying()
                VoiceAction.UNKNOWN -> Unit
            }
        }
    }



    private fun volumeUp() {
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_RAISE,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun volumeDown() {
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_LOWER,
            android.media.AudioManager.FLAG_SHOW_UI
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

        initializeVoskModel()

        // prefs
        initializePref()

        // SoundPool
        initializeSoundPool()

        startListening()
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
                    handler.post { duckStart() }
                }

                override fun onDone(utteranceId: String) {
                    handler.post { duckStop() }
                }

                override fun onError(utteranceId: String) {
                    handler.post { duckStop() }
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
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
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
            onPcm = { pcm ->
                // сюда переносишь ВСЁ что ниже создания pcm в твоём коде:
                // pendingResetToWake / pendingSwitchToCommand / isListeningCommand ...
                handlePcm(pcm)
            },
            onError = { msg ->
                Log.e(APPLICATION_NAME, msg)
                publishRecognizedText(msg)
            }
        )
    }

    private fun handlePcm(pcm: ByteArray) {
        val now = SystemClock.elapsedRealtime()

        if (pendingResetToWake) {
            pendingResetToWake = false
            Log.d(APPLICATION_NAME, "handlePcm: pendingResetToWake")
            resetToWakeModeInternal()
            return
        }

        if (pendingSwitchToCommand) {
            pendingSwitchToCommand = false
            Log.d(APPLICATION_NAME, "handlePcm: pendingSwitchToCommand")
            switchToCommandModeInternal()
        }

        if (isListeningCommand) {
            // ---------- COMMAND MODE ----------
            when (val r = vosk.acceptCommand(pcm)) {
                is VoskResult.Final -> {
                    Log.d(APPLICATION_NAME, "VoiceService:: CMD final: ${r.text}")
                    handleCommandText(r.text)
                }
                is VoskResult.Partial -> {
                    val partialText = r.text.trim()
                    if (partialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                        lastUiUpdateMs = now
                        publishRecognizedText("Команда: $partialText")
                    }
                }
                VoskResult.None -> Unit
            }

        } else {
            // ---------- WAKE MODE ----------
            when (val r = vosk.acceptWake(pcm)) {
                is VoskResult.Final -> {
                    val txt = normalize(r.text)
                    Log.d(APPLICATION_NAME, "VoiceService:: WAKE final: $txt")

                    when {
                        txt == "джарвис" -> {
                            val now2 = SystemClock.elapsedRealtime()
                            if (now2 - lastWakeTriggerMs >= WAKE_DEBOUNCE_MS) {
                                lastWakeTriggerMs = now2
                                publishRecognizedText("Джарвис! Слушаю команду...")
                                playHappy()
                                switchToCommandMode()
                            } else {
                                Log.d(APPLICATION_NAME, "Wake debounce: ignored")
                            }
                        }

                        txt.startsWith("джарвис ") -> {
                            playHappy()
                            val cmd = txt.removePrefix("джарвис ").trim()
                            Log.d(APPLICATION_NAME, "VoiceService:: WAKE cmd: $cmd")
                            publishRecognizedText("Выполняю: $cmd")

                            handleCommandText(cmd)

                            // остаёмся в WAKE (а handleCommandText сам поставит pendingResetToWake
                            // если ты внутри него вызываешь resetToWakeMode())
                            vosk.resetWake()
                        }

                        else -> {
                            // не наша фраза
                            vosk.resetWake()
                        }
                    }
                }

                is VoskResult.Partial -> {
                    val wakePartialText = r.text.trim()
                    if (wakePartialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                        lastUiUpdateMs = now
                        publishRecognizedText("Слышу: $wakePartialText")
                    }
                }

                VoskResult.None -> Unit
            }
        }
    }



    private fun switchToCommandModeInternal() {
        duckStart() // <-- ДО начала распознавания команды
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

        duckStop() // <-- ВСЕГДА отпускаем фокус при выходе из команд

        vosk.resetCommand()
        vosk.resetWake()

        playSad() //— оставь как тебе нужно (у тебя оно уже есть и тут, и в handleCommand)
        publishRecognizedText("Слушаю...")
        Log.d(APPLICATION_NAME, "VoiceService::resetToWakeModeInternal")
    }


    private fun switchToCommandMode() {
        pendingSwitchToCommand = true
    }

    private fun resetToWakeMode() {
        pendingResetToWake = true
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
        executor.execute(action)

        resetToWakeMode()
    }


    private fun handleCommand(resultJson: String) {
        handleCommandText(parseText(resultJson))
    }


    private fun pausePlayback() {
        val controller = getTopMediaController()
        if (controller == null) {
            Log.d(APPLICATION_NAME, "VoiceService::pausePlayback Нет активного плеера для PAUSE")
            return
        }
        controller.transportControls.pause()
        Log.d(APPLICATION_NAME, "VoiceService::pausePlayback pause отправлен в ${controller.packageName}")
    }

    private fun playPlayback() {
        val controller = getTopMediaController()
        if (controller == null) {
            Log.d(APPLICATION_NAME, "VoiceService::playPlayback Нет активного плеера для PLAY")
            return
        }
        controller.transportControls.play()
        Log.d(APPLICATION_NAME, "VoiceService::playPlayback play отправлен в ${controller.packageName}")
    }

    private fun playHappy() {
        val id = soundPool?.play(sndHappy, happyVol, happyVol, 1, 0, 1f) ?: 0
        Log.d(APPLICATION_NAME, "VoiceService::playHappy soundId=$sndHappy streamId=$id vol=$happyVol")
    }

    private fun playSad() {
        val id = soundPool?.play(sndSad, sadVol, sadVol, 1, 0, 1f) ?: 0
        Log.d(APPLICATION_NAME, "VoiceService::playSad soundId=$sndSad streamId=$id vol=$sadVol")
    }


    private fun parseText(jsonString: String): String {
        return try {
            JSONObject(jsonString).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }


    private fun parsePartial(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val partial = json.optString("partial")
            partial
        } catch (e: Exception) {
            Log.e(APPLICATION_NAME, "JsonString=${jsonString} JSON error: ${e.message}")
            ""
        }
    }

    private fun canControlMediaSessions(): Boolean {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        val cn = android.content.ComponentName(this, JarvisNotificationListener::class.java)

        val accessGranted = nm.isNotificationListenerAccessGranted(cn)
        val connected = JarvisNotificationListener.connected

        if (accessGranted && !connected) {
            android.service.notification.NotificationListenerService.requestRebind(cn)
            Log.d(APPLICATION_NAME, "requestRebind() called for NotificationListener")
        }

        Log.d(APPLICATION_NAME, "NotifAccess=$accessGranted, listenerConnected=$connected")
        return accessGranted && connected
    }


    private fun getTopMediaController(): android.media.session.MediaController? {
        if (!canControlMediaSessions()) {
            Log.e(
                APPLICATION_NAME,
                "VoiceService::getTopMediaController Notification access не готов: включите доступ и перезапустите приложение/сервис"
            )
            return null
        }

        val msm = getSystemService(MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val component = android.content.ComponentName(this, JarvisNotificationListener::class.java)

        return try {
            msm.getActiveSessions(component).firstOrNull()
        } catch (e: SecurityException) {
            Log.e(
                APPLICATION_NAME,
                "VoiceService::getTopMediaController SecurityException в getActiveSessions (listener ещё не активен)",
                e
            )
            null
        }
    }


    private fun nextTrack() {
        val controller = getTopMediaController()
        if (controller == null) {
            Log.d(APPLICATION_NAME, "VoiceService::nextTrack Нет активного плеера для NEXT")
            return
        }
        controller.transportControls.skipToNext()
        Log.d(APPLICATION_NAME, "️VoiceService::nextTrack skipToNext отправлен в ${controller.packageName}")
    }

    private fun prevTrack() {
        val controller = getTopMediaController()
        if (controller == null) {
            Log.d(APPLICATION_NAME, "VoiceService::prevTrack Нет активного плеера для PREV")
            return
        }
        controller.transportControls.skipToPrevious()
        Log.d(APPLICATION_NAME, "VoiceService:::prevTrack️ skipToPrevious отправлен в ${controller.packageName}")
    }


    override fun onDestroy() {
        handler.removeCallbacks(commandTimeoutRunnable)
        duckStop() // <-- на всякий случай

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

        super.onDestroy()
    }

    private fun speakNowPlaying() {
        val controller = getTopMediaController()
        if (controller == null) {
            speak("Не вижу активный плеер", "cmd_title_none")
            return
        }

        val md = controller.metadata
        val title = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)

        val t = title?.takeIf { it.isNotBlank() }
        val a = artist?.takeIf { it.isNotBlank() }

        val phrase = when {
            a != null && t != null -> "Сейчас играет: $a — $t"
            t != null -> "Сейчас играет: $t"
            else -> "Не удалось получить название трека"
        }

        speak(phrase, "cmd_title")
    }


    private fun speakTime() {
        val now = LocalTime.now()
        val hhmm = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        speak("Сейчас $hhmm", "cmd_time")
    }


    private fun speak(text: String, utteranceId: String) {
        handler.post {
            if (!ttsReady) {
                Log.w(APPLICATION_NAME, "TTS not ready, skip: $text")
                return@post
            }
            val params = Bundle()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    private fun duckStart() {
        if (duckActive) return

        val req = android.media.AudioFocusRequest.Builder(
            android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(afListener)
            .build()

        val granted = audioManager.requestAudioFocus(req) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(APPLICATION_NAME, "duckStart granted=$granted")

        if (granted) {
            focusReq = req
            duckActive = true
        }
    }

    private fun duckStop() {
        val req = focusReq ?: run {
            duckActive = false
            return
        }
        audioManager.abandonAudioFocusRequest(req)
        focusReq = null
        duckActive = false
        Log.d(APPLICATION_NAME, "duckStop")
    }


    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
}

