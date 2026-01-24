package ru.lsn03.voicemediacontroller.db

import ru.lsn03.voicemediacontroller.action.VoiceAction

object DbSeeder {

    fun defaultBindings(): List<Pair<VoiceAction, List<String>>> = listOf(
        VoiceAction.NEXT to listOf("следующий трек","следующий","некст","че за хуйня","что за хуйня"),
        VoiceAction.PREV to listOf("предыдущий трек","предыдущий","прев"),
        VoiceAction.STOP to listOf("пауза","стоп"),
        VoiceAction.START to listOf("продолжи","продолжить","возобнови","плей","плэй","играй","старт"),
        VoiceAction.VOLUME_DOWN to listOf("тише","уменьши"),
        VoiceAction.VOLUME_UP to listOf("громче","увеличь"),
        VoiceAction.SAY_TIME to listOf("время"),
        VoiceAction.SAY_TITLE to listOf("название")
    )
}