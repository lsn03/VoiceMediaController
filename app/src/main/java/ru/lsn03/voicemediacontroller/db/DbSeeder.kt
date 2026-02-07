package ru.lsn03.voicemediacontroller.db

import ru.lsn03.voicemediacontroller.action.VoiceAction

object DbSeeder {

    fun defaultBindings(): List<Pair<VoiceAction, List<String>>> = listOf(
        VoiceAction.NEXT to listOf("следующий трек", "следующий","некст"),
        VoiceAction.PREV to listOf("предыдущий трек","предыдущий","прев"),
        VoiceAction.STOP to listOf("стоп"),
        VoiceAction.START to listOf("старт", "продолжи","продолжить"),
        VoiceAction.VOLUME_DOWN to listOf("тише","уменьши"),
        VoiceAction.VOLUME_UP to listOf("громче","увеличь"),
        VoiceAction.SAY_TIME to listOf("время"),
        VoiceAction.SAY_TITLE to listOf("название")
    )

    fun defaultPhrases(action: VoiceAction): List<String> =
        defaultMap[action].orEmpty()

    private val defaultMap: Map<VoiceAction, List<String>> by lazy {
        defaultBindings().toMap()
    }
}