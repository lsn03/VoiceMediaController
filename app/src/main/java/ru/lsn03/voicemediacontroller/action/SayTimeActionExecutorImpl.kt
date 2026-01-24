package ru.lsn03.voicemediacontroller.action

import ru.lsn03.voicemediacontroller.tts.SpeechGateway
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SayTimeActionExecutorImpl (private val speechGateway: SpeechGateway) : ActionExecutor {
    override fun getAction(): VoiceAction {
        return  VoiceAction.SAY_TIME
    }

    override fun execute() {
        val now = LocalTime.now()
        val hhmm = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        speechGateway.speak("Сейчас $hhmm", "cmd_time")
    }
}