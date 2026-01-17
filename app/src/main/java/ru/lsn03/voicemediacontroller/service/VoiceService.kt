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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.MODEL_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
import java.io.File
import java.io.IOException


class VoiceService : Service() {

    private lateinit var model: Model
    private lateinit var wakeRecognizer: Recognizer  // Только "джарвис"
    private lateinit var commandRecognizer: Recognizer  // Полные команды

    companion object {
        val recognizedText = MutableLiveData<String>("Слушаю...")
        private var audioRecord: AudioRecord? = null
        private val SAMPLE_RATE = 16000
        private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var isListeningCommand = false
    private var lastUiUpdateMs = 0L
    private var lastWakeTriggerMs = 0L
    private val UI_THROTTLE_MS = 250L        // не чаще 4 раз/сек
    private val WAKE_DEBOUNCE_MS = 1200L     // защита от повторов "джарвис"

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
        Log.d(APPLICATION_NAME, "VoiceService::onStartCommand")

        val notification = createNotification()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        return START_STICKY
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

        model = Model(modelPath())

        // 👤 Wake word recognizer (маленькая грамматика)
//        wakeRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), """["джарвис"]""")
        wakeRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), """["джарвис", "[unk]"]""")

        // 🎵 Command recognizer (команды)
        commandRecognizer = Recognizer(
            model, SAMPLE_RATE.toFloat(),
            """["следующий трек","следующий", "предыдущий трек", "предыдущий", "некст","прев", "пауза", "стоп", "уменьши", "увеличь", "громче", "тише", "продолжить", "продолжи","возобнови","плей", "плэй", "играй","старт", "стоп"]"""
        )

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

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(APPLICATION_NAME, "No RECORD_AUDIO permission")
            recognizedText.postValue("Нет разрешения на микрофон")
            return
        }

        val listeningThread = Thread {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            audioRecord?.startRecording() ?: run {
                Log.e(APPLICATION_NAME, "AudioRecord failed to init")
                return@Thread
            }

            val buffer = ShortArray(bufferSize / 2)

            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read <= 0) continue

                val pcm = ByteArray(read * 2)
                for (i in 0 until read) {
                    pcm[i * 2] = (buffer[i].toInt() and 0x00ff).toByte()
                    pcm[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                }

                val now = SystemClock.elapsedRealtime()

                if (pendingResetToWake) {
                    pendingResetToWake = false
                    resetToWakeModeInternal()
                    continue
                }

                if (pendingSwitchToCommand) {
                    pendingSwitchToCommand = false
                    switchToCommandModeInternal()
                }

                if (isListeningCommand) {
                    // ---------- РЕЖИМ КОМАНД ----------
                    if (commandRecognizer.acceptWaveForm(pcm, pcm.size)) {
                        val result = commandRecognizer.result
                        Log.d(APPLICATION_NAME, "VoiceService:: CMD result: $result")
                        handleCommand(result)
                    } else {
                        val partialText = parsePartial(commandRecognizer.partialResult).trim()
                        if (partialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                            lastUiUpdateMs = now
                            recognizedText.postValue("Команда: $partialText")
                        }
                    }
                } else {
                    // ---------- РЕЖИМ WAKE ----------
                    val isFinal = wakeRecognizer.acceptWaveForm(pcm, pcm.size)

                    if (isFinal) {
                        // ВАЖНО: используем только финальный result
                        val wakeText = parseText(wakeRecognizer.result).trim().lowercase()

                        if (wakeText.contains("джарвис")) {
                            if (now - lastWakeTriggerMs >= WAKE_DEBOUNCE_MS) {
                                lastWakeTriggerMs = now
                                Log.d(APPLICATION_NAME, "VoiceService Wake by FINAL result: ${wakeRecognizer.result}")
                                recognizedText.postValue("Джарвис! Слушаю команду...")
                                switchToCommandMode()
                            }
                        } else {
                            // чтобы не копить состояние, когда финал не наш
                            wakeRecognizer.reset()
                        }
                    } else {
                        // partial — только для отображения (не для триггера!)
                        val wakePartialText = parsePartial(wakeRecognizer.partialResult).trim()
                        if (wakePartialText.isNotEmpty() && now - lastUiUpdateMs >= UI_THROTTLE_MS) {
                            lastUiUpdateMs = now
                            recognizedText.postValue("Слышу: $wakePartialText")
                        }
                    }
                }
            }
        }

        listeningThread.name = "ListeningThread"
        listeningThread.start()
    }


    private fun switchToCommandModeInternal() {
        isListeningCommand = true
        commandRecognizer.reset()
        recognizedText.postValue("Слушаю команду...")
        handler.removeCallbacks(commandTimeoutRunnable)
        handler.postDelayed(commandTimeoutRunnable, COMMAND_TIMEOUT_MS)
        Log.d(APPLICATION_NAME, "VoiceService::switchToCommandModeInternal")
    }

    private fun resetToWakeModeInternal() {
        isListeningCommand = false
        handler.removeCallbacks(commandTimeoutRunnable)
        wakeRecognizer.reset()
        commandRecognizer.reset()
        recognizedText.postValue("Слушаю...")
        Log.d(APPLICATION_NAME, "VoiceService::resetToWakeModeInternal")
    }

    private fun switchToCommandMode() {
        pendingSwitchToCommand = true
    }

    private fun resetToWakeMode() {
        pendingResetToWake = true
    }


    private fun handleCommand(resultJson: String) {
        val text = parseText(resultJson).trim()

        if (text.isEmpty()) {
            Log.d(APPLICATION_NAME, "⚠️ Пустая команда (скорее всего тишина) — остаюсь в режиме команд")
            // НЕ выходим в wake, пусть ещё слушает до таймаута
            return
        }

        Log.d(APPLICATION_NAME, "✅ Выполняю команду: $text")
        recognizedText.postValue("Выполняю: $text")

        when (text.lowercase()) {
            "следующий трек", "некст", "следующий" -> {
                nextTrack()
                Log.d(APPLICATION_NAME, "📱 Следующий трек")
            }

            "предыдущий трек", "прев", "предыдущий" -> {
                prevTrack()
                Log.d(APPLICATION_NAME, "⏮️ Предыдущий трек")
            }

            "пауза", "стоп" -> {
                pausePlayback()
                Log.d(APPLICATION_NAME, "⏸️ Пауза")
            }

            "уменьши", "тише" -> {
                volumeDown();
                Log.d(APPLICATION_NAME, "Уменьшить громкость")
            }

            "увеличь", "громче" -> {
                volumeUp()
                Log.d(APPLICATION_NAME, "Увеличь громкость")
            }

            "продолжи", "продолжить", "возобнови", "плей", "плэй", "играй", "старт" -> {
                playPlayback()
                Log.d(APPLICATION_NAME, "▶️ Продолжить")
            }

            else -> Log.d(APPLICATION_NAME, "Неизвестная команда: $text")
        }

        // Команда выполнена — теперь можно выходить в wake
        resetToWakeMode()

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
        super.onDestroy()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRunning = false
    }


    private fun modelPath(): String {
        val modelDir = File(cacheDir, MODEL_NAME)
        Log.d(APPLICATION_NAME, "VoiceService::modelPath Модель: ${modelDir.absolutePath}")

        if (modelDir.exists() && modelDir.listFiles()?.size ?: 0 > 5) {  // >5 файлов = OK
            Log.d(APPLICATION_NAME, "VoiceService::modelPath Модель готова: ${modelDir.listFiles()?.size} файлов")
            return modelDir.absolutePath
        }

        // Копируем ПАПКУ из assets
        try {
            copyAssetFolder(MODEL_NAME, modelDir)
            modelDir.setReadable(true, false)
            Log.d(APPLICATION_NAME, "VoiceService::modelPath Модель скопирована: ${modelDir.listFiles()?.size} файлов")
        } catch (e: IOException) {
            Log.e(APPLICATION_NAME, "VoiceService::modelPath Ошибка копирования модели", e)
            throw e
        }
        return modelDir.absolutePath
    }

    private fun copyAssetFolder(fromAssetPath: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        assets.list(fromAssetPath)?.forEach { child ->
            Log.d(APPLICATION_NAME, "VoiceService::copyAssetFolder child: $child")
            val childAsset = "$fromAssetPath/$child"
            val destFile = File(destDir, child)

            if (assets.list(childAsset)?.isNotEmpty() == true) {
                // Рекурсивно папка
                copyAssetFolder(childAsset, destFile)
            } else {
                // Файл
                assets.open(childAsset).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
}

