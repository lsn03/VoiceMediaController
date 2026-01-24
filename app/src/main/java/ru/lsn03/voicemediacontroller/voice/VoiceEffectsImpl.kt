package ru.lsn03.voicemediacontroller.voice

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.lsn03.voicemediacontroller.action.ActionExecutorProvider
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.audio.ducker.AudioDucker
import ru.lsn03.voicemediacontroller.audio.soundpool.SoundPoolProvider
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
    private val repo: VoiceCommandRepository,
) : VoiceEffects {

    @Volatile
    private var matcher: CommandMatcher = CommandMatcher(emptyList())

    fun startDbMatcher(scope: CoroutineScope) {
        scope.launch {
            repo.observeBindings().collect { bindings ->
                Log.d(APPLICATION_NAME, "Matcher updated: bindings=${bindings}")
                matcher = CommandMatcher(bindings)
                Log.d(APPLICATION_NAME, "matcher set id=${System.identityHashCode(matcher)} size=${bindings.size}")
            }
        }
    }

    override fun start(scope: CoroutineScope) = startDbMatcher(scope)

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