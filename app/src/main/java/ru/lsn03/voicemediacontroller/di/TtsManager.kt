package ru.lsn03.voicemediacontroller.di

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.lsn03.voicemediacontroller.service.VoiceService
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.ACTION_OPEN_TTS_INSTALL
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.ACTION_OPEN_TTS_SETTINGS
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.NOTIF_TTS_HELP_ID
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.VOICE_CHANNEL
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val handler: Handler,
) {
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    fun init(onStart: () -> Unit, onStop: () -> Unit) {
        if (tts != null) return

        tts = TextToSpeech(ctx) { status ->
            ready = (status == TextToSpeech.SUCCESS)
            Log.d(APPLICATION_NAME, "TtsManager::initialization TTS, status=$status")
            if (ready) {
                tts?.language = Locale.getDefault()

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        handler.post { onStart() }
                    }

                    override fun onDone(utteranceId: String) {
                        handler.post { onStop() }
                    }

                    override fun onError(utteranceId: String) {
                        handler.post { onStop() }
                    }
                })

            } else {
                Log.d(
                    APPLICATION_NAME,
                    "В системе не настроен движок синтеза речи. Нажми «Установить» или открой «Настройки»."
                )
                handler.post({ showTtsFixNotification("В системе не настроен движок синтеза речи...") })
            }
        }
    }

    fun speak(text: String, utteranceId: String) {
        handler.post {
            if (!ready) {
                Log.w(APPLICATION_NAME, "SpeechGateway: TTS not ready, skip: $text")
                return@post
            }
            val params = Bundle()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun shutdown() {
        handler.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ready = false
        }
    }

    fun openTtsInstall() {
        val i = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            Log.e(APPLICATION_NAME, "No activity for ACTION_INSTALL_TTS_DATA", e)
            openTtsSettings()
        }
    }

    fun openTtsSettings() {
        val i = Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun showTtsFixNotification(reason: String) {
        val installIntent = Intent(ctx, VoiceService::class.java).apply { action = ACTION_OPEN_TTS_INSTALL }
        val settingsIntent = Intent(ctx, VoiceService::class.java).apply { action = ACTION_OPEN_TTS_SETTINGS }

        val piInstall = PendingIntent.getService(
            ctx, 2001, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val piSettings = PendingIntent.getService(
            ctx, 2002, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, VOICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Нужен синтез речи (TTS)")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_save, "Установить", piInstall)
            .addAction(android.R.drawable.ic_menu_preferences, "Настройки", piSettings)
            .build()

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_TTS_HELP_ID, n)
    }

}
