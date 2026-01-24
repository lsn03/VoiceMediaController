package ru.lsn03.voicemediacontroller.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.command.CommandMatcher
import ru.lsn03.voicemediacontroller.events.VoiceEvents
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.vosk.VoskEngine

class VoiceEffectsImpl(
    private val applicationContext: Context,
    private val actionExecutorProvider: ActionExecutorProvider,
    private val audioDucker: AudioDucker,
    private val vosk: VoskEngine,
    private val soundPoolProvider: SoundPoolProvider,
) : VoiceEffects {

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

    override fun publishText(text: String) {
        val intent = Intent(VoiceEvents.ACTION_RECOGNIZED_TEXT).apply {
            putExtra(VoiceEvents.EXTRA_TEXT, text)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onFinalCommand(cmd: String) {
        val text = normalize(cmd)
        if (text.isEmpty()) {
            Log.d(APPLICATION_NAME, "Empty command text")
            return
        }
        Log.d(APPLICATION_NAME, "Command text: $text")
        publishText("Выполняю: $text")

        val action = matcher.match(text) ?: VoiceAction.UNKNOWN
        Log.d(APPLICATION_NAME, "Action=$action")

        val exec = actionExecutorProvider.getExecutor(action)

        exec.execute()

    }

    override fun enterWake() {
        audioDucker.stop()
        vosk.resetCommand()
        vosk.resetWake()
        soundPoolProvider.playSad()
        publishText("Слушаю...")
        Log.d(APPLICATION_NAME, "VoiceService::enterWake")
    }


    override fun enterCommand() {
        audioDucker.start()
        vosk.resetCommand()
        soundPoolProvider.playHappy()
        publishText("Слушаю команду...")
        Log.d(APPLICATION_NAME, "VoiceService::enterCommand")
    }

    private fun normalize(s: String) = s.trim().lowercase()

}